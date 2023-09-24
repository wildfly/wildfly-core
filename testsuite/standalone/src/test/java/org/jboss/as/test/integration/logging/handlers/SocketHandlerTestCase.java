/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.json.JsonObject;
import javax.security.auth.x500.X500Principal;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.JsonLogServer;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(SocketHandlerTestCase.ConfigureSubsystem.class)
public class SocketHandlerTestCase extends AbstractLoggingTestCase {

    private static final String HOSTNAME = TestSuiteEnvironment.getServerAddress();
    private static final int PORT = 10514;
    private static final int ALT_PORT = 11514;
    private static final int DFT_TIMEOUT = TimeoutUtil.adjust(Integer.parseInt(System.getProperty("org.jboss.as.logging.timeout", "3")) * 1000);
    private static final String SOCKET_BINDING_NAME = "log-server";
    private static final String FORMATTER_NAME = "json";
    private static final ModelNode LOGGER_ADDRESS = SUBSYSTEM_ADDRESS.append("logger", LoggingServiceActivator.LOGGER.getName()).toModelNode();
    private static final Path TEMP_DIR = createTempDir();

    // TLS Configuration
    private static final String TEST_PASSWORD = "changeit";
    private static final char[] KEYSTORE_CREATION_PASSWORD = TEST_PASSWORD.toCharArray();
    private static final String ALIAS = "selfsigned";
    private static final String SERVER_DNS_STRING = "CN=localhost, OU=Unknown, L=Unknown, ST=Unknown, C=Unknown";

    private final Deque<ModelNode> resourcesToRemove = new ArrayDeque<>();

    @BeforeClass
    public static void setup() throws Exception {
        deploy(createDeployment(), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        undeploy(DEPLOYMENT_NAME);
        // Clear the temporary directory and delete it
        Files.walkFileTree(TEMP_DIR, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        Files.deleteIfExists(TEMP_DIR);
    }

    @After
    public void cleanUp() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        ModelNode address;
        while ((address = resourcesToRemove.pollFirst()) != null) {
            final ModelNode op = Operations.createOperation("remove-handler", LOGGER_ADDRESS);
            op.get("name").set(getName(address));
            builder.addStep(op);
            builder.addStep(Operations.createRemoveOperation(address));
        }
        executeOperation(builder.build());
    }

    @Test
    public void testTcpSocket() throws Exception {
        // Create a TCP server and start it
        try (JsonLogServer server = JsonLogServer.createTcpServer(PORT)) {
            server.start(DFT_TIMEOUT);

            // Add the socket handler and test all levels
            final ModelNode socketHandlerAddress = addSocketHandler("test-log-server", null, null);
            checkLevelsLogged(server, EnumSet.allOf(Logger.Level.class), "Test TCP all levels.");

            // Change to only allowing INFO and higher messages
            executeOperation(Operations.createWriteAttributeOperation(socketHandlerAddress, "level", "INFO"));
            checkLevelsLogged(server, EnumSet.of(Logger.Level.INFO, Logger.Level.WARN, Logger.Level.ERROR, Logger.Level.FATAL), "Test TCP INFO and higher.");
        }
    }

    @Test
    public void testAsyncTcpSocket() throws Exception {
        // Create a TCP server and start it
        try (JsonLogServer server = JsonLogServer.createTcpServer(PORT)) {
            server.start(DFT_TIMEOUT);

            // Add the socket handler and test all levels
            final ModelNode socketHandlerAddress = addSocketHandler("test-async-log-server", null, null, null, true);
            checkLevelsLogged(server, EnumSet.allOf(Logger.Level.class), "Test TCP all levels.");
        }
    }

    @Test
    public void testTlsSocket() throws Exception {
        final KeyStore clientTrustStore = loadKeyStore();
        final KeyStore serverKeyStore = loadKeyStore();

        createKeyStoreTrustStore(serverKeyStore, clientTrustStore);
        final Path clientTrustFile = createTemporaryKeyStoreFile(clientTrustStore, "client-trust-store.jks");
        final Path serverCertFile = createTemporaryKeyStoreFile(serverKeyStore, "server-cert-store.jks");
        // Create a TCP server and start it
        try (JsonLogServer server = JsonLogServer.createTlsServer(PORT, serverCertFile, TEST_PASSWORD)) {
            server.start(DFT_TIMEOUT);

            // Add the socket handler and test all levels
            final ModelNode socketHandlerAddress = addSocketHandler("test-log-server", null, "SSL_TCP", clientTrustFile);
            checkLevelsLogged(server, EnumSet.allOf(Logger.Level.class), "Test SSL_TCP all levels.");

            // Change to only allowing INFO and higher messages
            executeOperation(Operations.createWriteAttributeOperation(socketHandlerAddress, "level", "INFO"));
            checkLevelsLogged(server, EnumSet.of(Logger.Level.INFO, Logger.Level.WARN, Logger.Level.ERROR, Logger.Level.FATAL), "Test SSL_TCP INFO and higher.");
        }
    }

    @Test
    public void testUdpSocket() throws Exception {
        // Create a UDP server and start it
        try (JsonLogServer server = JsonLogServer.createUdpServer(PORT)) {
            server.start(DFT_TIMEOUT);
            final ModelNode socketHandlerAddress = addSocketHandler("test-log-server", null, "UDP");
            checkLevelsLogged(server, EnumSet.allOf(Logger.Level.class), "Test UPD all levels.");

            // Change to only allowing INFO and higher messages
            executeOperation(Operations.createWriteAttributeOperation(socketHandlerAddress, "level", "INFO"));
            checkLevelsLogged(server, EnumSet.of(Logger.Level.INFO, Logger.Level.WARN, Logger.Level.ERROR, Logger.Level.FATAL), "Test UDP INFO and higher.");
        }
    }

    @Test
    public void testLevelChange() throws Exception {
        // Create a TCP server and start it
        try (JsonLogServer server = JsonLogServer.createTcpServer(PORT)) {
            server.start(DFT_TIMEOUT);

            // Add the socket handler and test all levels
            final ModelNode socketHandlerAddress = addSocketHandler("test-log-server", "WARN", null);
            checkLevelsLogged(server, EnumSet.of(Logger.Level.WARN, Logger.Level.ERROR, Logger.Level.FATAL), "Test TCP WARN and greater levels.");

            // Change to allow INFO and higher messages
            executeOperation(Operations.createWriteAttributeOperation(socketHandlerAddress, "level", "INFO"));
            checkLevelsLogged(server, EnumSet.of(Logger.Level.INFO, Logger.Level.WARN, Logger.Level.ERROR, Logger.Level.FATAL), "Test TCP INFO and higher.");
        }
    }

    @Test
    public void testWithFilter() throws Exception {
        // Create a TCP server and start it
        try (JsonLogServer server = JsonLogServer.createTcpServer(PORT)) {
            server.start(DFT_TIMEOUT);

            final ModelNode address = addSocketHandler("test-log-server", "INFO", "TCP");
            executeOperation(Operations.createWriteAttributeOperation(address, "filter-spec", "substituteAll(\"\\\\s\", \"_\")"));
            // We should end up with only 3 messages that should have the spaces removed and replaced with an underscore
            final List<JsonObject> foundMessages = executeRequest("test message",
                    Collections.singletonMap(LoggingServiceActivator.LOG_LEVELS_KEY, "INFO,WARN,ERROR"), server, 3);
            // Process the JSON messages
            for (JsonObject foundMessage : foundMessages) {
                final String msg = foundMessage.getString("message");
                Assert.assertEquals(String.format("Expected test_message but found %s for level %s.",
                        msg, foundMessage.getString("level")), "test_message", msg);
            }
        }
    }

    @Test
    public void testProtocolChange() throws Exception {
        final ModelNode address = addSocketHandler("test-log-server", "INFO", "TCP");
        // Create a TCP server and start it
        try (JsonLogServer server = JsonLogServer.createTcpServer(PORT)) {
            server.start(DFT_TIMEOUT);
            // We should end up with a single log INFO log message
            final JsonObject foundMessage = executeRequest("Test TCP message",
                    Collections.singletonMap(LoggingServiceActivator.LOG_LEVELS_KEY, "INFO"), server);
            Assert.assertEquals("Test TCP message", foundMessage.getString("message"));
            server.stop();
        }

        // Create a new UDP server
        try (JsonLogServer server = JsonLogServer.createUdpServer(PORT)) {
            server.start(DFT_TIMEOUT);

            // Change the protocol and ensure we can connect
            executeOperation(Operations.createWriteAttributeOperation(address, "protocol", "UDP"));
            final JsonObject foundMessage = executeRequest("Test UDP message",
                    Collections.singletonMap(LoggingServiceActivator.LOG_LEVELS_KEY, "INFO"), server);
            Assert.assertEquals("Test UDP message", foundMessage.getString("message"));
        }
    }

    @Test
    public void testOutboundSocketBindingChange() throws Exception {
        // Add a new outbound-socket-binding
        final String altName = "alt-log-server";
        final ModelNode outboundSocketBindingAddress = Operations.createAddress("socket-binding-group", "standard-sockets",
                "remote-destination-outbound-socket-binding", altName);
        ModelNode op = Operations.createAddOperation(outboundSocketBindingAddress);
        op.get("host").set(HOSTNAME);
        op.get("port").set(ALT_PORT);
        executeOperation(op);
        resourcesToRemove.addLast(outboundSocketBindingAddress);

        // Create the server, plus a new server listening on the alternate port
        try (JsonLogServer server = JsonLogServer.createTcpServer(PORT);
             JsonLogServer altServer = JsonLogServer.createTcpServer(ALT_PORT)
        ) {
            server.start(DFT_TIMEOUT);
            altServer.start(DFT_TIMEOUT);
            final ModelNode altAddress = addSocketHandler("test-log-server-alt", null, null);

            // Log a single message to the current log server and validate
            JsonObject foundMessage = executeRequest("Test first message",
                    Collections.singletonMap(LoggingServiceActivator.LOG_LEVELS_KEY, "INFO"), server);
            Assert.assertEquals("Test first message", foundMessage.getString("message"));

            // Change the outbound-socket-binding-ref, which should require a reload
            op = Operations.createWriteAttributeOperation(altAddress, "outbound-socket-binding-ref", altName);
            executeOperation(op);
            ServerReload.executeReloadAndWaitForCompletion(client.getControllerClient());

            // Log a single message to the alternate log server and validate
            foundMessage = executeRequest("Test alternate message",
                    Collections.singletonMap(LoggingServiceActivator.LOG_LEVELS_KEY, "INFO"), altServer);
            Assert.assertEquals("Test alternate message", foundMessage.getString("message"));
        }
    }

    private void checkLevelsLogged(final JsonLogServer server, final Set<Logger.Level> expectedLevels, final String msg) throws IOException, InterruptedException {
        executeRequest(msg, Collections.singletonMap("includeLevel", "true"));
        final List<JsonObject> foundMessages = new ArrayList<>();
        for (int i = 0; i < expectedLevels.size(); i++) {
            final JsonObject foundMessage = server.getLogMessage(DFT_TIMEOUT);
            if (foundMessage == null) {
                final String failureMessage = "A log messages was not received within " + DFT_TIMEOUT + " milliseconds." +
                        System.lineSeparator() +
                        "Found the following messages: " + foundMessages +
                        System.lineSeparator() +
                        "Expected the following levels to be logged: " + expectedLevels;
                Assert.fail(failureMessage);

            }
            foundMessages.add(foundMessage);
        }
        Assert.assertEquals(expectedLevels.size(), foundMessages.size());

        // Check that we have all the expected levels
        final Collection<String> levels = expectedLevels.stream()
                .map(Enum::name)
                .collect(Collectors.toList());
        final Iterator<JsonObject> iter = foundMessages.iterator();
        while (iter.hasNext()) {
            final JsonObject foundMessage = iter.next();
            final String foundLevel = foundMessage.getString("level");
            Assert.assertNotNull("Expected a level on " + foundMessage, foundLevel);
            Assert.assertTrue(String.format("Level %s was logged, but not expected.", foundLevel), levels.remove(foundLevel));
            iter.remove();
        }

        // The string levels should be empty, if not we're missing an expected level
        Assert.assertTrue("Found levels that did not appear to be logged: " + levels, levels.isEmpty());
    }

    private ModelNode addSocketHandler(final String name, final String level, final String protocol) throws IOException {
        return addSocketHandler(name, level, protocol, null);
    }

    private ModelNode addSocketHandler(final String name, final String level, final String protocol, final Path keyStore) throws IOException {
        return addSocketHandler(name, level, protocol, keyStore, false);
    }

    private ModelNode addSocketHandler(final String name, final String level, final String protocol, final Path keyStore,
                                       final boolean wrapInAsyncHandler) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Add a socket handler
        final ModelNode address = SUBSYSTEM_ADDRESS.append("socket-handler", name).toModelNode();
        ModelNode op = Operations.createAddOperation(address);
        op.get("named-formatter").set(FORMATTER_NAME);
        op.get("outbound-socket-binding-ref").set(SOCKET_BINDING_NAME);
        if (level != null) {
            op.get("level").set(level);
        }
        if (protocol != null) {
            op.get("protocol").set(protocol);
        }
        if (keyStore != null) {
            // We need to add the SSL context to Elytron
            final ModelNode keyStoreAddress = Operations.createAddress("subsystem", "elytron", "key-store", "log-test-ks");
            resourcesToRemove.addFirst(keyStoreAddress);
            final ModelNode keyStoreAddOp = Operations.createAddOperation(keyStoreAddress);
            keyStoreAddOp.get("path").set(keyStore.toAbsolutePath().toString());
            keyStoreAddOp.get("type").set("JKS");
            final ModelNode creds = keyStoreAddOp.get("credential-reference").setEmptyObject();
            creds.get("clear-text").set(TEST_PASSWORD);
            builder.addStep(keyStoreAddOp);

            final ModelNode keyManagerAddress = Operations.createAddress("subsystem", "elytron", "trust-manager", "log-test-tm");
            resourcesToRemove.addLast(keyManagerAddress);
            final ModelNode keyManagerAddOp = Operations.createAddOperation(keyManagerAddress);
            keyManagerAddOp.get("key-store").set("log-test-ks");
            builder.addStep(keyManagerAddOp);

            final ModelNode sslContextAddress = Operations.createAddress("subsystem", "elytron", "client-ssl-context", "log-test-ssl-context");
            resourcesToRemove.addLast(sslContextAddress);
            final ModelNode sslContextAddOp = Operations.createAddOperation(sslContextAddress);
            sslContextAddOp.get("trust-manager").set("log-test-tm");
            sslContextAddOp.get("protocols").setEmptyList().add("TLSv1.2");
            builder.addStep(sslContextAddOp);

            op.get("ssl-context").set("log-test-ssl-context");
        }
        builder.addStep(op);
        resourcesToRemove.addFirst(address);

        final String handlerName;

        if (wrapInAsyncHandler) {
            handlerName = "async";
            final ModelNode asyncHandlerAddress = SUBSYSTEM_ADDRESS.append("async-handler", handlerName).toModelNode();
            op = Operations.createAddOperation(asyncHandlerAddress);
            op.get("subhandlers").setEmptyList().add(name);
            if (level != null) {
                op.get("level").set(level);
            }
            op.get("queue-length").set(100L);
            builder.addStep(op);
            resourcesToRemove.addFirst(asyncHandlerAddress);
        } else {
            handlerName = name;
        }

        // Add the handler to the logger
        op = Operations.createOperation("add-handler", LOGGER_ADDRESS);
        op.get("name").set(handlerName);
        builder.addStep(op);
        executeOperation(builder.build());
        return address;
    }

    private static void executeRequest(final String msg, final Map<String, String> params) throws IOException {
        final int statusCode = getResponse(msg, params);
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);
    }

    private static JsonObject executeRequest(final String msg, final Map<String, String> params,
                                             final JsonLogServer server) throws IOException, InterruptedException {
        return executeRequest(msg, params, server, 1).get(0);
    }

    private static List<JsonObject> executeRequest(final String msg, final Map<String, String> params,
                                                   final JsonLogServer server, final int expectedMessages) throws IOException, InterruptedException {
        executeRequest(msg, params);
        final List<JsonObject> messages = new ArrayList<>(expectedMessages);
        for (int i = 0; i < expectedMessages; i++) {
            final JsonObject foundMessage = server.getLogMessage(DFT_TIMEOUT);
            if (foundMessage == null) {
                final String failureMessage = "A log messages was not received within " + DFT_TIMEOUT + " milliseconds." +
                        System.lineSeparator() +
                        "Found the following messages: " + messages;
                Assert.fail(failureMessage);

            }
            messages.add(foundMessage);
        }
        return messages;
    }

    private static String getName(final ModelNode address) {
        Assert.assertEquals(ModelType.LIST, address.getType());
        String name = "";
        for (Property property : address.asPropertyList()) {
            name = property.getValue().asString();
        }
        return name;
    }

    private static void createKeyStoreTrustStore(final KeyStore keyStore, final KeyStore trustStore) throws KeyStoreException {
        final X500Principal principal = new X500Principal(SERVER_DNS_STRING);

        final SelfSignedX509CertificateAndSigningKey selfSignedX509CertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                .setKeyAlgorithmName("RSA")
                .setSignatureAlgorithmName("SHA1withRSA")
                .setDn(principal)
                .setKeySize(1024)
                .build();
        final X509Certificate certificate = selfSignedX509CertificateAndSigningKey.getSelfSignedCertificate();

        keyStore.setKeyEntry(ALIAS, selfSignedX509CertificateAndSigningKey.getSigningKey(), KEYSTORE_CREATION_PASSWORD, new X509Certificate[] {certificate});
        trustStore.setCertificateEntry(ALIAS, certificate);
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        return ks;
    }

    private static Path createTemporaryKeyStoreFile(final KeyStore keyStore, final String fileName) throws Exception {
        final Path file = TEMP_DIR.resolve(fileName);
        try (OutputStream fos = Files.newOutputStream(file)) {
            keyStore.store(fos, KEYSTORE_CREATION_PASSWORD);
        }
        return file;
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("wf-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class ConfigureSubsystem implements ServerSetupTask {
        private ModelNode describeResult;

        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            // Describe the current subsystem to restore in the tearDown
            final ModelNode describeOp = Operations.createOperation("describe", SUBSYSTEM_ADDRESS.toModelNode());
            describeResult = executeOperation(managementClient, describeOp);

            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            final ModelNode outboundSocketBindingAddress = Operations.createAddress("socket-binding-group", "standard-sockets",
                    "remote-destination-outbound-socket-binding", SOCKET_BINDING_NAME);
            ModelNode op = Operations.createAddOperation(outboundSocketBindingAddress);
            op.get("host").set(HOSTNAME);
            op.get("port").set(PORT);
            builder.addStep(op);

            final ModelNode formatterAddress = SUBSYSTEM_ADDRESS.append("json-formatter", FORMATTER_NAME).toModelNode();
            builder.addStep(Operations.createAddOperation(formatterAddress));

            builder.addStep(Operations.createAddOperation(LOGGER_ADDRESS));

            executeOperation(managementClient, builder.build());
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {
            try {
                if (describeResult != null) {
                    final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
                    // First remove the subsystem, then read add based on the describe op.
                    builder.addStep(Operations.createRemoveOperation(SUBSYSTEM_ADDRESS.toModelNode()));
                    for (ModelNode addOp : describeResult.asList()) {
                        builder.addStep(addOp);
                    }
                    executeOperation(managementClient, builder.build());
                }
            } finally {
                // Reload if required
                final ModelNode op = Operations.createReadAttributeOperation(new ModelNode().setEmptyList(), "server-state");
                ModelNode result = executeOperation(managementClient, op);
                if (ClientConstants.CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED.equals(result.asString())) {
                    ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
                }
            }
        }

        private ModelNode executeOperation(final ManagementClient managementClient, final ModelNode op) throws IOException {
            final ModelNode result = managementClient.getControllerClient().execute(op);
            if (!Operations.isSuccessfulOutcome(result)) {
                throw new RuntimeException(Operations.getFailureDescription(result).toString());
            }
            return Operations.readResult(result);
        }

        private void executeOperation(final ManagementClient managementClient, final Operation op) throws IOException {
            final ModelNode result = managementClient.getControllerClient().execute(op);
            if (!Operations.isSuccessfulOutcome(result)) {
                throw new RuntimeException(Operations.getFailureDescription(result).toString());
            }
        }

    }
}
