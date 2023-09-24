/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain;

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
import static org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil.SECONDARY_HOST_PASSWORD;
import static org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil.SECONDARY_HOST_USERNAME;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_HTTP_PORT;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

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
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;

/**
 * Common base for tests of SSL secured communication between primary and secondary.
 *
 * @author Ondrej Kotek <okotek@redhat.com>
 */
public abstract class AbstractSSLPrimarySecondaryTestCase {

    protected static final String PRIMARY_MANAGEMENT_REALM = "PrimaryManagementRealm";
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

    protected static void setPrimaryManagementNativeInterfaceAndCheck(ModelControllerClient client) throws Exception {
        checkHostStatusOnPrimary("secondary");
        setPrimaryManagementNativeInterface(client);
        reloadPrimary();
        checkHostStatusOnPrimary("primary");
    }

    protected static void setOriginPrimaryManagementNativeInterfaceAndCheck() throws Exception {
        setOriginPrimaryManagementNativeInterface();
        reloadPrimary();
        checkHostStatusOnPrimary("primary");
    }

    protected static void reloadPrimary() throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set("reload");
        op.get(OP_ADDR).add(HOST, "primary");
        op.get(ADMIN_ONLY).set(false);
        executeOverHttp(getPrimaryMgmtUri(), op.toJSONString(true));
    }

    protected static void checkHostStatusOnPrimary(String host) throws Exception {
        final URI mgmtURI = getPrimaryMgmtUri();
        final String operation = createHostStateRunningOperationJson(host);
        final long time = System.currentTimeMillis() + TIMEOUT;
        do {
            if (isHostStateRunning(mgmtURI, operation)) {
                return;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < time);

        Assert.fail("Cannot validate host '" + host + "' is running");
    }

    protected static void checkHostStatusOnPrimaryOverRemote(String host, DomainClient domainClient) throws IOException, InterruptedException {
        final long time = System.currentTimeMillis() + TIMEOUT;
        do {
            if (checkResultIsRunning(domainClient.execute(getHostStateRunningOperation(host)))) {
                return;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < time);

        Assert.fail("Cannot validate host '" + host + "' is running");
    }

    private static void setPrimaryManagementNativeInterface(ModelControllerClient client) throws Exception {
        ModelNode operation = createOpNode("host=primary/core-service=management/management-interface=native-interface",
                ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get("name").set("security-realm");
        operation.get("value").set(PRIMARY_MANAGEMENT_REALM);
        CoreUtils.applyUpdate(operation, client);
    }

    private static void setOriginPrimaryManagementNativeInterface() throws Exception {
        ModelNode operation = createOpNode("host=primary/core-service=management/management-interface=native-interface",
                ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        operation.get("name").set("security-realm");
        operation.get("value").set("ManagementRealm");
        executeOverHttp(getPrimaryMgmtUri(), operation.toJSONString(true));
    }

    private static URI getPrimaryMgmtUri() throws MalformedURLException, URISyntaxException {
        return new URL("http", DomainTestSupport.primaryAddress, MANAGEMENT_HTTP_PORT, MGMT_CTX).toURI();
    }

    private static boolean checkResultIsRunning(ModelNode result) {
        if (result != null && result.get(OUTCOME).asString().equals(SUCCESS)) {
            final ModelNode resultNode = result.require(RESULT);
            if ("running".equalsIgnoreCase(resultNode.asString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHostStateRunning(URI mgmtURI, String operation) throws IOException {
        ModelNode responseNode;
        try {
            responseNode = executeOverHttp(mgmtURI, operation);
        } catch (IOException ex) {
            // connection refused, host is not running
            return false;
        }

        return checkResultIsRunning(responseNode);
    }

    private static ModelNode executeOverHttp(URI mgmtURI, String operation) throws IOException {
        CloseableHttpClient httpClient = createHttpClient(mgmtURI);
        HttpEntity operationEntity = new StringEntity(operation, ContentType.APPLICATION_JSON);
        HttpPost httpPost = new HttpPost(mgmtURI);
        httpPost.setEntity(operationEntity);

        HttpResponse response;
        ModelNode responseNode;
        try {
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
        } finally {
            httpClient.close();
        }

        return responseNode;
    }

    private static CloseableHttpClient createHttpClient(URI mgmtURI) {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(SECONDARY_HOST_USERNAME, SECONDARY_HOST_PASSWORD);
        AuthScope authScope = new AuthScope(mgmtURI.getHost(), mgmtURI.getPort());
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(authScope, credentials);
        return HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();
    }

    private static String createHostStateRunningOperationJson(String host) {
        return getHostStateRunningOperation(host).toJSONString(true);
    }

    private static ModelNode getHostStateRunningOperation(String host) {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).add(HOST, host);
        operation.get(NAME).set(HOST_STATE);
        return operation;
    }
}
