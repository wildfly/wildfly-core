/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.VALUE;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.Random;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.DeploymentArchiveUtils;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

import jakarta.inject.Inject;

/**
 * This is sort of franken test. Aim is to test if CLI/model will overcome undertow restriction on upload limit after protocol
 * upgrade and to ensure that bare mgmt upload will be restricted by it.
 *
 * @author bbaranow@ibm.com
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class InterfaceUploadLimitTestCase {
    private static final PathAddress PROPERTY_ADDR = PathAddress.pathAddress(ModelDescriptionConstants.SYSTEM_PROPERTY,
            "org.wildfly.management.upload.limit");
    private static final String HTTP_PATH = "/management-upload";
    private static final String MANAGEMENT_REALM = "ManagementRealm";

    private static final String ARCHIVE_NAME_BIG = "BIG_berta.war";
    private static final int CONTENT_SIZE_BIG = 8659208; //~8.6MB in Bytes
    private static final String ARCHIVE_NAME_MEDIUM = "MEDium_berta.war";
    private static final int CONTENT_SIZE_MEDIUM = CONTENT_SIZE_BIG - 2000000;// to make it less than our default
    private static final String ARCHIVE_NAME_SMALL = "small_berta.war";
    private static final int CONTENT_SIZE_SMALL = 2167615; // 2.1MB, slightly bigger than default undertow 2MB
    private static final String ARCHIVE_NAME_TINY = "tiny_berta.war";
    private static final int CONTENT_SIZE_TINY = 2167615 / 2;
    private static final int BIG_MEDIUM_BERTA_LIMIT = CONTENT_SIZE_BIG - 1000000;
    private static final int RIDICULOUSLY_SMALL_UPLOAD_LIMIT = 2000;

    @Inject
    private ServerController serverController;

    @Test
    public void testDefaultUndertowLimit_BareUpload() throws Exception {
        // test if non upgrade is subject to default undertow restrictions
        // don't change properties
        serverController.start();
        try {
            final ManagementClient client = serverController.getClient();
            final Archive<?> ARCHIVE_HANDLE_TINY = createWarArchive(ARCHIVE_NAME_TINY, CONTENT_SIZE_TINY);
            File tmpContentHandle = getArchiveBinaryFileHandle(ARCHIVE_NAME_TINY);
            File archiveExportHandle = getArchiveFileHandle(ARCHIVE_HANDLE_TINY);
            try {
                deployOverHttp(client, ARCHIVE_HANDLE_TINY); // this should always pass as deployment is below undertow limit
            } catch (SocketException e) {
                fail(e.getMessage());
            } finally {
                try {
                    DeploymentArchiveUtils.undeploy(client, ARCHIVE_HANDLE_TINY.getName());
                } finally { // just in case, if something goes off, lets try to clean up
                    tmpContentHandle.delete();
                    archiveExportHandle.delete();
                }
            }
            final Archive<?> ARCHIVE_HANDLE_SMALL = createWarArchive(ARCHIVE_NAME_SMALL, CONTENT_SIZE_SMALL);
            tmpContentHandle = getArchiveBinaryFileHandle(ARCHIVE_NAME_SMALL);
            archiveExportHandle = getArchiveFileHandle(ARCHIVE_HANDLE_SMALL);
            try {
                deployOverHttp(client, ARCHIVE_HANDLE_SMALL); // this will fail if default undertow limit is enforced. If it
                                                              // doesnt, something makes it bigger
            } catch (SocketException e) {
                fail(e.getMessage());
            } finally {
                try {
                    DeploymentArchiveUtils.undeploy(client, ARCHIVE_HANDLE_SMALL.getName());
                } finally {// just in case, if something goes off, lets try to clean up
                    tmpContentHandle.delete();
                    archiveExportHandle.delete();
                }
            }
        } finally {
            serverController.stop();
        }
    }

    @Test
    public void testUploadLimitWithProtocolUpgrade_NO_PROPERTY() throws Exception { // blow up
        // Check if archive over default undertow limit and under management default limit will work.
        // This test is relevant in context of other
        serverController.start();
        try {
            final ManagementClient client = serverController.getClient();
            final Archive<?> ARCHIVE_HANDLE_BIG = createWarArchive(ARCHIVE_NAME_BIG, CONTENT_SIZE_BIG);
            File tmpContentHandle = getArchiveBinaryFileHandle(ARCHIVE_NAME_BIG);
            try {
                DeploymentArchiveUtils.deploy(ARCHIVE_HANDLE_BIG, client);
            } finally {
                tmpContentHandle.delete();
                DeploymentArchiveUtils.undeploy(client, ARCHIVE_HANDLE_BIG.getName());
            }
        } finally {
            serverController.stop();
        }
    }

    @Test
    public void testUploadLimitWithProtocolUpgrade_WITH_PROPERTY() throws Exception {
        // check if some small default will affect upload over protocol upgrade
        serverController.start();
        try {
            final ManagementClient client = serverController.getClient();
            final Archive<?> ARCHIVE_HANDLE_BIG = createWarArchive(ARCHIVE_NAME_BIG, CONTENT_SIZE_BIG);
            File tmpContentHandle = getArchiveBinaryFileHandle(ARCHIVE_NAME_BIG);
            try {
                ModelNode op = Util.createAddOperation(PROPERTY_ADDR);
                op.get(ModelDescriptionConstants.VALUE).set(RIDICULOUSLY_SMALL_UPLOAD_LIMIT);
                ModelNode result = client.executeForResult(op);
                op = Util.getReadResourceOperation(PROPERTY_ADDR);
                result = client.executeForResult(op);
                ServerReload.executeReloadAndWaitForCompletion(client.getControllerClient());
                Assert.assertEquals(RIDICULOUSLY_SMALL_UPLOAD_LIMIT, result.get(VALUE).asInt());
                DeploymentArchiveUtils.deploy(ARCHIVE_HANDLE_BIG, client);
            } finally {
                try {
                    DeploymentArchiveUtils.undeploy(client, ARCHIVE_HANDLE_BIG.getName());
                    ModelNode op = Util.createRemoveOperation(PROPERTY_ADDR);
                    client.executeForResult(op);
                } finally {
                    tmpContentHandle.delete();
                }
            }
        } finally {
            serverController.stop();
        }
    }

    @Test
    public void testUploadLimitBareUpload_WITH_PROPERTY() throws Exception {
        // check if OUR default upload limit will work
        serverController.start();
        try {
            final ManagementClient client = serverController.getClient();
            final Archive<?> ARCHIVE_HANDLE_MEDIUM = createWarArchive(ARCHIVE_NAME_MEDIUM, CONTENT_SIZE_MEDIUM);
            File tmpContentHandle = getArchiveBinaryFileHandle(ARCHIVE_NAME_MEDIUM);
            File archiveExportHandle = getArchiveFileHandle(ARCHIVE_HANDLE_MEDIUM);
            try {
                ModelNode op = Util.createAddOperation(PROPERTY_ADDR);
                op.get(ModelDescriptionConstants.VALUE).set(BIG_MEDIUM_BERTA_LIMIT);
                ModelNode result = client.executeForResult(op);
                op = Util.getReadResourceOperation(PROPERTY_ADDR);
                result = client.executeForResult(op);
                Assert.assertEquals(BIG_MEDIUM_BERTA_LIMIT, result.get(VALUE).asInt());
                ServerReload.executeReloadAndWaitForCompletion(client.getControllerClient());
                deployOverHttp(client, ARCHIVE_HANDLE_MEDIUM);// this should deploy fine as archive is slightly below default
                                                              // limit
            } catch (SocketException e) {
                fail(e.getMessage());
            } finally {
                try {
                    DeploymentArchiveUtils.undeploy(client, ARCHIVE_HANDLE_MEDIUM.getName());
                } finally { // just in case, if something goes off, lets try to clean up
                    tmpContentHandle.delete();
                    archiveExportHandle.delete();
                }
            }
            final Archive<?> ARCHIVE_HANDLE_BIG = createWarArchive(ARCHIVE_NAME_BIG, CONTENT_SIZE_BIG);
            tmpContentHandle = getArchiveBinaryFileHandle(ARCHIVE_NAME_BIG);
            archiveExportHandle = getArchiveFileHandle(ARCHIVE_HANDLE_BIG);
            try {
                deployOverHttp(client, ARCHIVE_HANDLE_BIG);
                fail("Upload limit not honored!");
            } catch (SocketException e) {
                //this is correct, DONT check message as it depends on OS/source
            } finally {
                try {
                    DeploymentArchiveUtils.undeploy(client, ARCHIVE_HANDLE_BIG.getName());
                    ModelNode op = Util.createRemoveOperation(PROPERTY_ADDR);
                    client.executeForResult(op);
                } finally { // just in case, if something goes off, lets try to clean up
                    tmpContentHandle.delete();
                    archiveExportHandle.delete();
                }
            }
        } finally {
            serverController.stop();
        }
    }

    private void deployOverHttp(final ManagementClient managementClient, Archive<?> archiveHandle) throws Exception {
        final String host = managementClient.getMgmtAddress();
        final int port = managementClient.getMgmtPort();
        final URI uri = new URI("http://" + host + ":" + port + HTTP_PATH);
        try (final CloseableHttpClient httpClient = createHttpClient(host, port, Authentication.USERNAME,
                Authentication.PASSWORD)) {

            final ModelNode deploymentOne = new ModelNode();
            deploymentOne.get(OP).set(ADD);
            deploymentOne.get(OP_ADDR).set(DEPLOYMENT, archiveHandle.getName());
            deploymentOne.get(ENABLED).set(true);
            deploymentOne.get(CONTENT).add().get(INPUT_STREAM_INDEX).set(0);
            final ContentBody operation = new StringBody(deploymentOne.toJSONString(true), ContentType.APPLICATION_JSON);
            final File fileHandle = new File(TestSuiteEnvironment.getTmpDir(), archiveHandle.getName());

            fileHandle.deleteOnExit();
            try {
                new ZipExporterImpl(archiveHandle).exportTo(fileHandle, true);
                final ContentBody deploymentContent = new FileBody(fileHandle);
                final ModelNode result = executePost(uri, httpClient, operation, deploymentContent);
                // in case of tripping limit, this will throw SocketException: Broken pipe
                // otherwise lets check
                Assert.assertEquals(result.asString(), SUCCESS, result.get(OUTCOME).asString());
            } finally {
                fileHandle.delete();
            }
        }
    }

    private ModelNode executePost(final URI uri, final CloseableHttpClient httpClient, final ContentBody operation,
            final ContentBody deploymentContent) throws Exception {
        final HttpPost post = new HttpPost(uri);
        post.setHeader("X-Management-Client-Name", "test-client");
        final MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create().addPart("operation", operation);
        entityBuilder.addPart("input-streams", deploymentContent);
        post.setEntity(entityBuilder.build());
        return parseResponse(httpClient.execute(post));
    }

    private ModelNode parseResponse(HttpResponse response) {
        try {
            String content = EntityUtils.toString(response.getEntity());
            int status = response.getStatusLine().getStatusCode();
            ModelNode modelResponse;
            if (status == HttpStatus.SC_OK) {
                modelResponse = ModelNode.fromJSONString(content);
            } else {
                modelResponse = new ModelNode();
                modelResponse.get(OUTCOME).set(FAILED);
                modelResponse.get(FAILURE_DESCRIPTION).set(content);
            }
            return modelResponse;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read response content as String");
        }
    }

    private static CloseableHttpClient createHttpClient(String host, int port, String username, String password) {
        try {
            SSLContext sslContext = SSLContexts.createDefault();
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
                    NoopHostnameVerifier.INSTANCE);
            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
                    .register("https", sslConnectionSocketFactory)
                    .register("http", PlainConnectionSocketFactory.getSocketFactory()).build();
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(host, port, MANAGEMENT_REALM, AuthSchemes.DIGEST),
                    new UsernamePasswordCredentials(username, password));
            PoolingHttpClientConnectionManager connectionPool = new PoolingHttpClientConnectionManager(registry);
            HttpClientBuilder.create().setConnectionManager(connectionPool).build();
            return HttpClientBuilder.create().setConnectionManager(connectionPool)
                    .setRetryHandler(new StandardHttpRequestRetryHandler(5, true)).setDefaultCredentialsProvider(credsProvider)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static File createContentFile(final String archiveName, final int limit) throws Exception {
        final byte[] ding = new byte[10000];
        final Random rand = new Random();
        // needs to be random, since Archive will use file name to fetch content. if its not distinct content can be mixed
        // between archive objects and mess up archive size on export
        final File handle = getArchiveBinaryFileHandle(archiveName);
        handle.deleteOnExit(); // JIC
        try (FileOutputStream fos = new FileOutputStream(handle);) {
            long mark = 0;
            while (mark < limit) {
                // rand bytes will make it impossible for Zip to compress it at all.
                // So size of archive will be slightly bigger than designed limit(given other files and dictionary structures
                rand.nextBytes(ding);
                if (mark + ding.length > limit) {
                    fos.write(ding, 0, Long.valueOf(limit - mark).intValue());
                    break;
                } else {
                    fos.write(ding);
                    mark += ding.length;
                }
            }
            return handle;
        }
    }

    public static Archive<?> createWarArchive(final String archiveName, final int size) throws Exception {
        final File content = createContentFile(archiveName, size);
        WebArchive war = ShrinkWrap.create(WebArchive.class, archiveName);
        war.addAsWebResource(new FileAsset(content), "page.html");
        return war;
    }

    public static File getArchiveBinaryFileHandle(final String archiveName) {
        return new File(TestSuiteEnvironment.getTmpDir(), "content_tmp" + archiveName + ".bin");
    }

    public static File getArchiveFileHandle(final Archive<?> archiveHandle) {
        return new File(TestSuiteEnvironment.getTmpDir(), archiveHandle.getName());
    }
}
