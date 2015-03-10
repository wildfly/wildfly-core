/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.domain;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil.SLAVE_HOST_PASSWORD;
import static org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil.SLAVE_HOST_USERNAME;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_HTTP_PORT;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;

/**
 * Common base for tests of SSL secured communication between master and slave.
 *
 * @author Ondrej Kotek <okotek@redhat.com>
 */
public abstract class AbstractSSLMasterSlaveTestCase {

    protected static final String MASTER_MANAGEMENT_REALM = "MasterManagementRealm";
    private static final String MGMT_CTX = "/management";
    private static final int TIMEOUT = 60000;


    protected static void keyMaterialSetup(File workDir) throws Exception {
        // create key and trust stores with imported certificates from opposing sides
        FileUtils.deleteDirectory(workDir);
        workDir.mkdirs();
        Assert.assertTrue(workDir.exists());
        Assert.assertTrue(workDir.isDirectory());
        CoreUtils.createKeyMaterial(workDir);
    }

    protected static void setMasterManagementNativeInterface(ModelControllerClient client) throws Exception {
        ModelNode operation = createOpNode("host=master/core-service=management/management-interface=native-interface",
                ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get("name").set("security-realm");
        operation.get("value").set(MASTER_MANAGEMENT_REALM);
        CoreUtils.applyUpdate(operation, client);
    }

    protected static void setOriginMasterManagementNativeInterface() throws Exception {
        ModelNode operation = createOpNode("host=master/core-service=management/management-interface=native-interface",
                ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get("name").set("security-realm");
        operation.get("value").set("ManagementRealm");
        executeOverHttp(getMasterMgmtUri(), operation.toJSONString(true));
    }

    protected static void reloadMaster() throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set("reload");
        op.get(OP_ADDR).add(HOST, "master");
        op.get(ADMIN_ONLY).set(false);
        executeOverHttp(getMasterMgmtUri(), op.toJSONString(true));
    }

    protected static void checkHostStatusOnMaster(String host) throws Exception {
        final URI mgmtURI = getMasterMgmtUri();
        final String operation = getHostStateRunningOperationJson(host);
        final long time = System.currentTimeMillis() + TIMEOUT;
        do {
            Thread.sleep(1000);
            if (isHostStateRunning(mgmtURI, operation)) {
                return;
            }
        } while (System.currentTimeMillis() < time);

        Assert.fail("Cannot validate host '" + host + "' is running");
    }

    private static URI getMasterMgmtUri() throws MalformedURLException, URISyntaxException {
        return new URL("http", DomainTestSupport.masterAddress, MANAGEMENT_HTTP_PORT, MGMT_CTX).toURI();
    }

    private static boolean isHostStateRunning(URI mgmtURI, String operation) throws IOException {
        ModelNode responseNode = executeOverHttp(mgmtURI, operation);
        if (responseNode != null && responseNode.get(OUTCOME).asString().equals(SUCCESS)) {
            final ModelNode resultNode = responseNode.require(RESULT);
            if ("running".equalsIgnoreCase(resultNode.asString())) {
                return true;
            }
        }

        return false;
    }

    private static ModelNode executeOverHttp(URI mgmtURI, String operation) throws IOException {
        CloseableHttpClient httpClient = getHttpClient(mgmtURI);
        HttpEntity operationEntity = new StringEntity(operation, ContentType.APPLICATION_JSON);
        HttpPost httpPost = new HttpPost(mgmtURI);
        httpPost.setEntity(operationEntity);

        HttpResponse response;
        ModelNode responseNode;
        try{
            response = httpClient.execute(httpPost);

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                return null;
            }

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return null;
            }
            responseNode = ModelNode.fromJSONStream(response.getEntity().getContent());
            EntityUtils.consume(entity);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        } finally {
            httpClient.close();
        }

        return responseNode;
    }

    private static CloseableHttpClient getHttpClient(URI mgmtURI) {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(SLAVE_HOST_USERNAME, SLAVE_HOST_PASSWORD);
        AuthScope authScope = new AuthScope(mgmtURI.getHost(), mgmtURI.getPort());
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(authScope, credentials);
        return HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();
    }

    private static String getHostStateRunningOperationJson(String host) {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).add(HOST, host);
        operation.get(NAME).set(HOST_STATE);
        return operation.toJSONString(true);
    }
}
