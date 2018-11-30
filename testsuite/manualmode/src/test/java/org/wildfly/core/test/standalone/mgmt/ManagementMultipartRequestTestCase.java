package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KEYSTORE_PASSWORD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KEYSTORE_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECURE_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECURITY_REALM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_IDENTITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SSL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;
import static org.jboss.as.domain.management.ModelDescriptionConstants.DEFAULT_USER;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_HTTPS_PORT;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_NATIVE_PORT;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.wildfly.core.test.standalone.mgmt.HTTPSManagementInterfaceTestCase.getNativeModelControllerClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import javax.inject.Inject;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codehaus.plexus.util.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.security.adduser.AddUser;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.integration.security.common.SSLTruststoreUtil;
import org.jboss.as.test.integration.security.common.SecurityTestConstants;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;


/**
 * Test case for https://issues.jboss.org/browse/WFCORE-4149
 * <p>
 * Parts of multipart http request were thrown away when:
 * <li>legacy security realm is used,</li>
 * <li>truststore auth is configured,</li>
 * <li>DIGEST auth is actually used,</li>
 * <li>content part was larger then ?.</li>
 */
@ServerControl(manual = true)
@RunWith(WildflyTestRunner.class)
public class ManagementMultipartRequestTestCase {

    private static final Logger LOG = Logger.getLogger(ManagementMultipartRequestTestCase.class);

    private static final String TEST_NAME = ManagementMultipartRequestTestCase.class.getSimpleName();
    private static final String DEPLOYMENT_NAME = "deployment.war";
    private static final String MANAGEMENT_REALM = "ManagementRealm";
    private static final String MANAGEMENT_NATIVE_REALM = "ManagementNativeRealm";

    private static final File WORK_DIR = new File("target" + File.separatorChar + TEST_NAME);
    private static final File SERVER_KEYSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_KEYSTORE);
    private static final File SERVER_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.SERVER_TRUSTSTORE);
    private static final File CLIENT_TRUSTSTORE_FILE = new File(WORK_DIR, SecurityTestConstants.CLIENT_TRUSTSTORE);
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "Admin#123";

    private static final Path MGMT_USERS_PROPERTY_PATH = Paths.get(TestSuiteEnvironment.getJBossHome(),
            "/standalone/configuration/", AddUser.MGMT_USERS_PROPERTIES);

    private static final ServerSetup mutualSslSetup = new ServerSetup();

    @SuppressWarnings("unused")
    @Inject
    private static ServerController controller;

    @BeforeClass
    public static void startAndSetupContainer() throws Exception {
        keyMaterialSetup();
        createManagementUsers();

        controller.startInAdminMode();

        try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
            mutualSslSetup.setup(client);
            reload(client);
        }
    }

    @AfterClass
    public static void stopContainer() throws Exception {
        try (ModelControllerClient client = getNativeModelControllerClient()) {
            mutualSslSetup.tearDown(client);
        }

        controller.stop();

        deleteManagementUsers();
        deleteKeyMaterials();
    }

    /**
     * Test multipart request by sending a deploy operation with a WAR over https management interface.
     */
    @Test
    public void testMultipartRequest() throws Exception {
        URI managementUri = new URI("https", null, getServerAddress(), MANAGEMENT_HTTPS_PORT,
                "/management-upload", null, null);

        // create POST request
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addPart("uploadFormElement", createDeploymentContentPart());
        builder.addPart("operation", createDeploymentOperationPart());
        HttpEntity entity = builder.build();
        HttpPost post = new HttpPost(managementUri);
        post.setHeader("X-Management-Client-Name", "HAL");
        post.setEntity(entity);


        // client intentionally doesn't use certificate to authenticate, even though truststore auth is configured,
        // but uses DIGEST instead

        HttpClient client = SSLTruststoreUtil.getHttpClientWithSSL(CLIENT_TRUSTSTORE_FILE.getAbsoluteFile(),
                SecurityTestConstants.KEYSTORE_PASSWORD);

        // credentials
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USERNAME, PASSWORD));

        // digest auth
        AuthCache authCache = new BasicAuthCache();
        DigestScheme digestScheme = new DigestScheme();
        authCache.put(new HttpHost(managementUri.getHost()), digestScheme);

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setAuthCache(authCache);

        // request
        try {
            HttpResponse response = client.execute(post, context);
            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        } finally {
            ((CloseableHttpClient) client).close();
        }
    }

    private static ContentBody createDeploymentOperationPart() throws IOException {
        ModelNode op = Util.createAddOperation(PathAddress.pathAddress(DEPLOYMENT, DEPLOYMENT_NAME));
        op.get("enabled").set(true);
        op.get("content").add().get(INPUT_STREAM_INDEX).set(0);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        op.writeBase64(baos);

        return new ByteArrayBody(baos.toByteArray(), ContentType.create("application/dmr-encoded"), "blob");
    }

    private static ContentBody createDeploymentContentPart() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        WebArchive war = createDeployment();
        war.as(ZipExporter.class).exportTo(baos);
        LOG.infof("Deployment size is %d bytes", baos.size());
        Assert.assertTrue("Generated war is too small, add some more classes!", baos.size() > 20000);

        return new ByteArrayBody(baos.toByteArray(), ContentType.create("application/x-webarchive"), "deployment.war");
    }

    private static WebArchive createDeployment() {
        // include whatever classes, but need to reach certain minimal size
        return ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME)
                .addPackage(ShrinkWrap.class.getPackage());
    }

    private static void keyMaterialSetup() throws Exception {
        FileUtils.deleteDirectory(WORK_DIR);
        Assert.assertTrue(WORK_DIR.mkdirs());
        CoreUtils.createKeyMaterial(WORK_DIR);
    }

    private static void deleteKeyMaterials() throws Exception {
        FileUtils.deleteDirectory(WORK_DIR);
    }

    private static void createManagementUsers() throws NoSuchAlgorithmException, IOException {
        String passwordHash = new UsernamePasswordHashUtil()
                .generateHashedHexURP(USERNAME, MANAGEMENT_REALM, PASSWORD.toCharArray());
        String line = String.format("\n%s=%s\n", USERNAME, passwordHash);
        Files.write(MGMT_USERS_PROPERTY_PATH, line.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void deleteManagementUsers() throws IOException {
        Files.write(MGMT_USERS_PROPERTY_PATH, "\n".getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String getServerAddress() throws UnknownHostException {
        return InetAddress.getByName(TestSuiteEnvironment.getServerAddress()).getHostAddress();
    }

    private static void reload(ModelControllerClient client) {
        ServerReload.executeReloadAndWaitForCompletion(client, ServerReload.TIMEOUT, false, "remoting",
                TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT);
    }

    /**
     * Mutual SSL setup and test cleanup.
     */
    static class ServerSetup {

        private static final PathAddress MANAGEMENT_NATIVE_REALM_ADDR = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT)
                .append(SECURITY_REALM, MANAGEMENT_NATIVE_REALM);

        private static final PathAddress TRUSTSTORE_AUTH_ADDR = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT)
                .append(SECURITY_REALM, MANAGEMENT_REALM)
                .append(AUTHENTICATION, TRUSTSTORE);

        private static final PathAddress SSL_IDENTITY_ADDR = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT)
                .append(SECURITY_REALM, MANAGEMENT_REALM)
                .append(SERVER_IDENTITY, SSL);

        private static final PathAddress MGMT_IFACE_ADDR = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT)
                .append(MANAGEMENT_INTERFACE, HTTP_INTERFACE);

        private static final PathAddress DEPLOYMENT_ADDR = PathAddress.pathAddress(DEPLOYMENT, DEPLOYMENT_NAME);

        public void setup(ModelControllerClient client) throws Exception {
            // intentionally add truststore authentication, even though client doesn't use it
            //core-service=management/security-realm=ManagementRealm/authentication=truststore:add(keystore-path=truststore.jks, keystore-password="secret")
            {
                ModelNode operation = Util.createOperation(ADD, TRUSTSTORE_AUTH_ADDR);
                operation.get(KEYSTORE_PATH).set(SERVER_TRUSTSTORE_FILE.getAbsolutePath());
                operation.get(KEYSTORE_PASSWORD).set(SecurityTestConstants.KEYSTORE_PASSWORD);
                CoreUtils.applyUpdate(operation, client);
            }

            // add ssl identity
            //core-service=management/security-realm=ManagementRealm/server-identity=ssl:add(keystore-path=server.keystore, keystore-password="secret")
            {
                ModelNode operation = Util.createOperation(ADD, SSL_IDENTITY_ADDR);
                operation.get(KEYSTORE_PATH).set(SERVER_KEYSTORE_FILE.getAbsolutePath());
                operation.get(KEYSTORE_PASSWORD).set(SecurityTestConstants.KEYSTORE_PASSWORD);
                CoreUtils.applyUpdate(operation, client);
            }

            // switch management interface to ssl
            //core-service=management/management-interface=http-interface:write-attribute(name=secure-socket-binding, value=management-https)
            {
                ModelNode operation = Operations
                        .createWriteAttributeOperation(MGMT_IFACE_ADDR.toModelNode(), SECURE_SOCKET_BINDING, "management-https");
                CoreUtils.applyUpdate(operation, client);
            }

            //core-service=management/management-interface=http-interface:undefine-attribute(name=socket-binding)
            {
                ModelNode operation = Operations
                        .createUndefineAttributeOperation(MGMT_IFACE_ADDR.toModelNode(), SOCKET_BINDING);
                CoreUtils.applyUpdate(operation, client);
            }

            // create another realm for native management interface
            {
                ModelNode operation = Operations
                        .createAddOperation(MANAGEMENT_NATIVE_REALM_ADDR.toModelNode());
                CoreUtils.applyUpdate(operation, client);
            }

            // add local auth
            {
                ModelNode operation = Operations
                        .createAddOperation(MANAGEMENT_NATIVE_REALM_ADDR.append(AUTHENTICATION, LOCAL).toModelNode());
                operation.get(DEFAULT_USER).set("$local");
                CoreUtils.applyUpdate(operation, client);
            }

            // add native socket binding
            {
                ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.ADD);
                operation.get("port").set(MANAGEMENT_NATIVE_PORT);
                operation.get("interface").set("management");
                CoreUtils.applyUpdate(operation, client);
            }

            // create native interface to control server while http interface will be secured
            {
                ModelNode operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.ADD);
                operation.get("security-realm").set(MANAGEMENT_NATIVE_REALM);
                operation.get("socket-binding").set("management-native");
                CoreUtils.applyUpdate(operation, client);
            }
        }

        public void tearDown(ModelControllerClient client) throws Exception {
            //core-service=management/security-realm=ManagementRealm/authentication=truststore:remove
            {
                ModelNode operation = Util.createOperation(REMOVE, TRUSTSTORE_AUTH_ADDR);
                CoreUtils.applyUpdate(operation, client);
            }

            //core-service=management/security-realm=ManagementRealm/server-identity=ssl:remove
            {
                ModelNode operation = Util.createOperation(REMOVE, SSL_IDENTITY_ADDR);
                CoreUtils.applyUpdate(operation, client);
            }

            //core-service=management/management-interface=http-interface:write-attribute(name=socket-binding, value=management-http)
            {
                ModelNode operation = Operations
                        .createWriteAttributeOperation(MGMT_IFACE_ADDR.toModelNode(), SOCKET_BINDING, "management-http");
                CoreUtils.applyUpdate(operation, client);
            }

            //core-service=management/management-interface=http-interface:undefine-attribute(name=secure-socket-binding)
            {
                ModelNode operation = Operations
                        .createUndefineAttributeOperation(MGMT_IFACE_ADDR.toModelNode(), SECURE_SOCKET_BINDING);
                CoreUtils.applyUpdate(operation, client);
            }

            // remove native management interface
            {
                ModelNode operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.REMOVE);
                CoreUtils.applyUpdate(operation, client);
            }

            // remove native socket binding
            {
                ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.REMOVE);
                CoreUtils.applyUpdate(operation, client);
            }

            // remove ManagementNativeRealm
            {
                ModelNode operation = Operations
                        .createRemoveOperation(MANAGEMENT_NATIVE_REALM_ADDR.toModelNode());
                CoreUtils.applyUpdate(operation, client);
            }

            // remove deployment
            {
                ModelNode operation = Operations.createRemoveOperation(DEPLOYMENT_ADDR.toModelNode());
                CoreUtils.applyUpdate(operation, client);
            }
        }
    }
}
