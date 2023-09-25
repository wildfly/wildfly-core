/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.http;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import jakarta.inject.Inject;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.http.Authentication;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test the HTTP API upload functionality to ensure that a deployment is successfully
 * transferred to the HTTP Server and processed by the model controller.
 *
 * @author Jonathan Pearlin
 */
@RunWith(WildFlyRunner.class)
public class HttpDeploymentUploadUnitTestCase {

    private static final String BOUNDARY_PARAM = "NeAG1QNIHHOyB5joAS7Rox!!";

    public static final String MANAGEMENT_URL_PART = "management";

    private static final String UPLOAD_URL_PART = "add-content";

    private static final String DEPLOYMENT_NAME = "test-http-deployment.sar";

    @Inject
    private ManagementClient managementClient;

    private final ModelNode deploymentAddress = Operations.createAddress("deployment", DEPLOYMENT_NAME);


    @Test
    public void testHttpDeploymentUpload() throws Exception {
        final String basicUrl = "http://" + managementClient.getMgmtAddress() + ":" + managementClient.getMgmtPort() + "/" + MANAGEMENT_URL_PART;
        final String uploadUrl = basicUrl + "/" + UPLOAD_URL_PART;
        final Path deploymentFile = Files.createTempFile("test-http-deployment", ".sar");

        try (CloseableHttpClient client = createHttpClient()) {
            // Create the deployment
            final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(
                    DEPLOYMENT_NAME, Collections.emptyMap());
            // Create the HTTP connection to the upload URL
            final HttpPost addContentPost = new HttpPost(uploadUrl);

            // Create a deployment file
            archive.as(ZipExporter.class).exportTo(deploymentFile.toFile(), true);
            addContentPost.setEntity(createUploadEntity(deploymentFile.toFile()));

            final byte[] hash;

            // Execute the request and get the HTTP response
            try (CloseableHttpResponse response = client.execute(addContentPost)) {
                final ModelNode result = validateStatus(response);
                hash = Operations.readResult(result).asBytes();
                // JBAS-9291
                assertEquals("text/html; charset=utf-8", response.getEntity().getContentType().getValue());
            }

            final HttpPost addHashContentPost = new HttpPost(basicUrl);
            addHashContentPost.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            addHashContentPost.setEntity(createAddEntity(hash));
            try (CloseableHttpResponse response = client.execute(addHashContentPost)) {
                validateStatus(response);
            }

            // Remove the deployment
            final HttpPost removePost = new HttpPost(basicUrl);
            removePost.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            removePost.setEntity(createRemoveEntity());
            try (CloseableHttpResponse response = client.execute(removePost)) {
                validateStatus(response);
            }
        } finally {
            Files.deleteIfExists(deploymentFile);
            boolean found = false;
            final ModelControllerClient client = managementClient.getControllerClient();
            // Use the management client to ensure we removed the deployment
            final ModelNode readOp = Operations.createOperation("read-children-names");
            readOp.get("child-type").set("deployment");
            ModelNode result = client.execute(readOp);
            if (Operations.isSuccessfulOutcome(result)) {
                final List<ModelNode> deployments = Operations.readResult(result).asList();
                for (ModelNode deployment : deployments) {
                    if (deployment.asString().equals(DEPLOYMENT_NAME)) {
                        found = true;
                        break;
                    }
                }
            }
            if (found) {
                result = client.execute(Operations.createRemoveOperation(deploymentAddress));
                if (!Operations.isSuccessfulOutcome(result)) {
                    fail(String.format("Failed to remove deployment %s: %s", DEPLOYMENT_NAME, Operations.getFailureDescription(result).asString()));
                }
            }
        }
    }

    private HttpEntity createAddEntity(final byte[] hash) throws IOException {
        final ModelNode op = Operations.createAddOperation(deploymentAddress);
        op.get("content").get(0).get("hash").set(hash);
        op.get("enabled").set(true);
        return new StringEntity(op.toJSONString(true));
    }

    private HttpEntity createRemoveEntity() throws IOException {
        return new StringEntity(Operations.createRemoveOperation(deploymentAddress).toJSONString(true));
    }

    private HttpEntity createUploadEntity(final File archive) {
        return MultipartEntityBuilder.create()
                .setContentType(ContentType.MULTIPART_FORM_DATA)
                .setBoundary(BOUNDARY_PARAM)
                .addTextBody("test1", BOUNDARY_PARAM)
                .addTextBody("test2", BOUNDARY_PARAM)
                .addBinaryBody("file", archive, ContentType.APPLICATION_OCTET_STREAM, DEPLOYMENT_NAME)
                .build();
    }

    private CloseableHttpClient createHttpClient() {
        try {
            final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .build();
            final CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(managementClient.getMgmtAddress(), managementClient.getMgmtPort()),
                    new UsernamePasswordCredentials(Authentication.USERNAME, Authentication.PASSWORD));
            return HttpClientBuilder.create()
                    .setConnectionManager(new PoolingHttpClientConnectionManager(registry))
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ModelNode validateStatus(final HttpResponse response) throws IOException {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final InputStream in = response.getEntity().getContent();
            final byte[] buffer = new byte[64];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            fail(out.toString());
        }
        final HttpEntity entity = response.getEntity();
        final ModelNode result = ModelNode.fromJSONStream(entity.getContent());
        assertNotNull(result);
        assertTrue(Operations.isSuccessfulOutcome(result));
        return result;
    }
}
