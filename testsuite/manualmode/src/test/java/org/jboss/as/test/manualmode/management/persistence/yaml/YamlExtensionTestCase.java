/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.persistence.yaml;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_MODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.hamcrest.CoreMatchers;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.Server;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Simple test to check that we can apply YAML configuration over an existing standard configuration.
 * Checking that reloading doesn't break the resulting configuration.
 * Testing the cli ops are compatible with the YAML changes.
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class YamlExtensionTestCase {

    private static final Logger log = Logger.getLogger(YamlExtensionTestCase.class);

    private static final ModelNode READ_CONFIG = Util.createEmptyOperation("read-config-as-xml", PathAddress.EMPTY_ADDRESS);

    @Inject
    private ServerController container;

    private static final Path jbossHome = System.getProperty("ts.bootable") != null ? Path.of(WildFlySecurityManager.getPropertyPrivileged("basedir", "."),
            "target" , "bootable-jar-build-artifacts", "wildfly") : Path.of(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "toto"));
    private static final Path basedir = jbossHome.resolve("standalone");
    private static Path markerDirectory;
    private static Path testYaml;
    private static Path testSocketOverrideYaml;
    private static Path testPortOffsetOverrideYaml;
    private static Path testRemoveSocketYaml;
    private static Path testAddingExtensionPathDeploymentOverlayIgnored;
    private static Path testAddingEmptyExtensionFailYaml;
    private static Path testDeploymentYaml;
    private static Path testManagedDeploymentYaml;
    private static Path testReplacingByEmptyResourceYaml;
    private static Path testWrongIndentationYaml;
    private static Path testNonExistentAttributeYaml;
    private static Path testOperationsYaml;
    private static Path testUndefineNonExistentAttributeYamlOperations;
    private static Path testRemoveAttributeRemovesAboveResourceYamlOperations;
    private static Path testRemoveNonExistentResource;
    private static Path testListAddOperationToStringFails;
    private static Path testListAddOperationToNonExistentResourceFails;
    private static Path cliScript;
    private static String defaultXml;
    private static String expectedXml;
    private static String expectedBootCLiXml;
    private static String originalJvmArgs;
    private static String originalModulePath;

    @BeforeClass
    public static void setup() throws Exception {
        Assume.assumeTrue("Layer testing provides a different XML file than the standard one which results in failures", System.getProperty("ts.layers") == null);
        Path configurationDir = basedir.resolve("configuration");
        Path referenceConfiguration = configurationDir.resolve("reference-standalone.xml");
        Files.copy(configurationDir.resolve("standalone.xml"), referenceConfiguration, REPLACE_EXISTING);

        // Provide correct module path for CLIWrapper when running in ts.bootable profile
        originalModulePath = WildFlySecurityManager.getPropertyPrivileged("module.path", null);
        WildFlySecurityManager.setPropertyPrivileged("module.path", jbossHome.resolve("modules").toString());

        try (CLIWrapper cli = new CLIWrapper(false)) {
            cli.sendLine("embed-server --admin-only --server-config=reference-standalone.xml --jboss-home=" + jbossHome);
            cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=http:add(interface=public)");
            cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=https:add()");
            cli.sendLine("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=mail-snmt:add(host=foo, port=8081)");
            cli.sendLine("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=foo2:add(host=foo2, port=8082)");
            cli.quit();
        }
        Path referenceCliConfiguration = configurationDir.resolve("reference-cli-standalone.xml");
        Files.copy(configurationDir.resolve("standalone.xml"), referenceCliConfiguration, REPLACE_EXISTING);
        Files.copy(getResourceFilePath("bootable-groups.properties"), configurationDir.resolve("bootable-groups.properties"), REPLACE_EXISTING);
        Files.copy(getResourceFilePath("bootable-users.properties"), configurationDir.resolve("bootable-users.properties"), REPLACE_EXISTING);
        try (CLIWrapper cli = new CLIWrapper(false)) {
            cli.sendLine("embed-server --admin-only --server-config=reference-cli-standalone.xml --jboss-home=" + jbossHome);
            cli.sendLine("/system-property=foo:add(value=bar)");
            cli.sendLine("/subsystem=elytron/properties-realm=bootable-realm:add(users-properties={path=bootable-users.properties, plain-text=true, relative-to=jboss.server.config.dir}, groups-properties={path=bootable-groups.properties, relative-to=jboss.server.config.dir})");
            cli.sendLine("/subsystem=elytron/security-domain=BootableDomain:add(default-realm=bootable-realm, permission-mapper=default-permission-mapper, realms=[{realm=bootable-realm, role-decoder=groups-to-roles}])");
            cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=http:add(interface=public)");
            cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=https:add()");
            cli.sendLine("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=mail-snmt:add(host=foo, port=8081)");
            cli.sendLine("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=foo2:add(host=foo2, port=8082)");
            cli.quit();
        }
        testYaml = getResourceFilePath("test.yml");
        testSocketOverrideYaml = getResourceFilePath("test-socket-override.yml");
        testPortOffsetOverrideYaml = getResourceFilePath("test-port-offset-override.yml");
        testRemoveSocketYaml = getResourceFilePath("test-remove-socket.yml");
        testAddingExtensionPathDeploymentOverlayIgnored = getResourceFilePath("test-adding-extension-path-deployment-overlay-ignored.yml");
        testAddingEmptyExtensionFailYaml = getResourceFilePath("test-adding-empty-extension.yml");
        testDeploymentYaml = getResourceFilePath("test-deployment.yml");
        testManagedDeploymentYaml = getResourceFilePath("test-managed-deployment.yml");
        testReplacingByEmptyResourceYaml = getResourceFilePath("test-replacing-by-empty-resource.yml");
        testWrongIndentationYaml = getResourceFilePath("test-indentation-wrong.yml");
        testNonExistentAttributeYaml = getResourceFilePath("test-setting-non-existent-attribute.yml");
        testOperationsYaml = getResourceFilePath("test-operations.yml");
        testUndefineNonExistentAttributeYamlOperations = getResourceFilePath("test-undefine-non-existent-attribute.yml");
        testRemoveAttributeRemovesAboveResourceYamlOperations = getResourceFilePath("test-remove-attribute-removes-above-resource.yml");
        testRemoveNonExistentResource = getResourceFilePath("test-remove-non-existent-resource.yml");
        testListAddOperationToStringFails = getResourceFilePath("test-list-add-operation-to-string-fails.yml");
        testListAddOperationToNonExistentResourceFails = getResourceFilePath("test-list-add-operation-to-non-existent-resource.yml");
        createDeployment(configurationDir.getParent().resolve("test.jar"));
        cliScript = getResourceFilePath("test.cli");
        defaultXml = loadFile(configurationDir.resolve("standalone.xml")).replace("\"", "'");
        expectedXml = loadFile(referenceConfiguration).replace("\"", "'");
        expectedBootCLiXml = loadFile(referenceCliConfiguration).replace("\"", "'");
        originalJvmArgs = WildFlySecurityManager.getPropertyPrivileged("jvm.args", null);
        Path target = Path.of("target");
        markerDirectory = Files.createDirectories(target.resolve("yaml").resolve("cli-boot-ops"));
        Files.copy(getResourceFilePath("bootable-groups.properties"),
                jbossHome.resolve("standalone").resolve("configuration").resolve("bootable-groups.properties"), REPLACE_EXISTING);
        Files.copy(getResourceFilePath("bootable-users.properties"),
                jbossHome.resolve("standalone").resolve("configuration").resolve("bootable-users.properties"), REPLACE_EXISTING);
    }

    private static Path getResourceFilePath(String filename) throws URISyntaxException {
        return Path.of(YamlExtensionTestCase.class.getResource(filename).toURI()).toAbsolutePath();
    }

    private static String loadFile(Path file) throws IOException {
        Iterator<String> iter = Files.readAllLines(file).iterator();
        StringBuilder builder = new StringBuilder();
        while (iter.hasNext()) {
            String cleanLine = removeWhiteSpaces(iter.next());
            if (!cleanLine.isBlank()) {
                builder.append(cleanLine);
                if (iter.hasNext()) {
                    builder.append("\n");
                }
            }
        }
        return builder.toString();
    }

    @After
    public void tearDown() {
        if (container.isStarted()) {
            container.stop(true);
        }
        if (originalJvmArgs != null) {
            WildFlySecurityManager.setPropertyPrivileged("jvm.args", originalJvmArgs);
        } else {
            WildFlySecurityManager.clearPropertyPrivileged("jvm.args");
        }
        if (originalModulePath != null) {
            WildFlySecurityManager.setPropertyPrivileged("module.path", originalModulePath);
        } else {
            WildFlySecurityManager.clearPropertyPrivileged("module.path");
        }
    }

    @Test
    public void testSimpleYaml() throws Exception {
        container.startYamlExtension(new Path[]{testYaml});
        Assert.assertEquals("Yaml changes to configuration were persisted to xml. This should never happen as it's in read-only mode.", expectedXml, readConfigAsXml());
        // read model and verify that test.yml changes are there
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        ModelNode outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8081", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8082", outboundSocketBindings.get("foo2").get("port").asString());
    }

    @Test
    public void testAddingExtensionPathDeploymentOverlayYamlLogsWarnings() throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testAddingExtensionPathDeploymentOverlayIgnored});

        // check no extension added
        ModelControllerClient client = container.getClient().getControllerClient();
        Assert.assertEquals("Extension must not be created but it was.",
                "failed", client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("extension", "org.jboss.as.failure").toModelNode())).get("outcome").asString());
        // check WARN logged
        String consoleOutput = byteArrayOutputStream.toString();
        assertThat("Information that adding extension is ignored is missing in log: " + consoleOutput, consoleOutput, CoreMatchers.containsString("WARN  [org.jboss.as.controller.management-operation] (main) WFLYCTL0508: The yaml element 'extension' and its sub-elements are ignored. Thus ignoring element 'org.jboss.as.failure: {module: org.jboss.as.failure}'."));
        assertThat("Information that adding deployment-overlay is ignored is missing in log: " + consoleOutput, consoleOutput, CoreMatchers.containsString("WARN  [org.jboss.as.controller.management-operation] (main) WFLYCTL0508: The yaml element 'deployment-overlay' and its sub-elements are ignored. Thus ignoring element '{dummy-overlay: null}'."));
        assertThat("Information that adding path is ignored is missing in log: " + consoleOutput, consoleOutput, CoreMatchers.containsString("WARN  [org.jboss.as.controller.management-operation] (main) WFLYCTL0508: The yaml element 'path' and its sub-elements are ignored. Thus ignoring element 'test.path: {relative-to: jboss.home.dir, path: bin}'."));
    }

    @Test
    public void testEmptyExtensionInYamlLogsWarnings() throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testAddingEmptyExtensionFailYaml});

        // check no extension added
        ModelControllerClient client = container.getClient().getControllerClient();
        Assert.assertEquals("Extension must not be created but it was.",
                "failed", client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("extension", "org.jboss.as.failure").toModelNode())).get("outcome").asString());
        // check WARN logged
        assertThat("Information that adding path is ignored is missing in log: " + byteArrayOutputStream, byteArrayOutputStream.toString(), CoreMatchers.containsString("WFLYCTL0508: The yaml element 'extension' and its sub-elements are ignored."));
    }

    @Test
    public void testDeploymentYaml() throws Exception {
        container.startYamlExtension(new Path[]{testDeploymentYaml});
        try (ModelControllerClient client = container.getClient().getControllerClient()) {
            ModelNode deployment = readDeployment(client, "test.jar");
            Assert.assertEquals("test.jar", deployment.get(NAME).asString());
            Assert.assertEquals("test.jar", deployment.get(RUNTIME_NAME).asString());
            Assert.assertEquals("true", deployment.get(ENABLED).asString());
            ModelNode contentItemNode = deployment.get(CONTENT).get(0);
            Assert.assertEquals("test.jar", contentItemNode.get(PATH).asString());
            Assert.assertEquals("jboss.server.base.dir", contentItemNode.get(RELATIVE_TO).asString());
            Assert.assertTrue(contentItemNode.get(ARCHIVE).asBoolean());
            deployment = readDeployment(client, "hello.jar");
            Assert.assertEquals("hello.jar", deployment.get(NAME).asString());
            Assert.assertEquals("hello.jar", deployment.get(RUNTIME_NAME).asString());
            contentItemNode = deployment.get(CONTENT).get(0);
            Assert.assertEquals("test.jar", contentItemNode.get(PATH).asString());
            Assert.assertEquals("jboss.server.base.dir", contentItemNode.get(RELATIVE_TO).asString());
            Assert.assertTrue(contentItemNode.get(ARCHIVE).asBoolean());
        }
    }

    /**
     * Managed deployments are not supported. We should fail
     */
    @Test
    public void testServerStartFailedForManagedDeployment() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testManagedDeploymentYaml});
            Assert.assertFalse(String.format("Failed to start, but did not throw an exception: %s", byteArrayOutputStream), container.isStarted());
        } catch (RuntimeException ex) {
            Assert.assertFalse(String.format("Failed to start with exception: %s%n%s", ex, byteArrayOutputStream), container.isStarted());
        }
    }

    private static void createDeployment(Path deployment) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.add(new StringAsset("Dependencies: =org.jboss.modules"), "META-INF/MANIFEST.MF");
        try (OutputStream out = Files.newOutputStream(deployment, StandardOpenOption.CREATE_NEW)) {
            archive.as(ZipExporter.class).exportTo(out);
        }
    }

    @Test
    public void testSimpleYamlWithReload() throws Exception {
        container.startYamlExtension(new Path[]{testYaml});
        Assert.assertEquals("Yaml changes to configuration were persisted to xml. This should never happen as it's in read-only mode.", expectedXml, readConfigAsXml());
        container.reload(TimeoutUtil.adjust(5000));
        compareXML(expectedXml, readConfigAsXml());
    }

    @Test
    public void testSimpleYamlWithCliBootOps() throws Exception {
        Assume.assumeTrue("Boot CLI script can be used on only in admin-only mode which is no valid for bootable jar.", System.getProperty("ts.bootable") == null);
        StringBuilder sb = new StringBuilder();
        sb.append(" -D" + AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY + "=").append(markerDirectory.toAbsolutePath());
        sb.append(" -D" + AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY + "=").append(cliScript.toAbsolutePath());
        // Propagate this property since it has the maven repository information which is needed on CI
        if (WildFlySecurityManager.getPropertyPrivileged("cli.jvm.args", null) != null) {
            sb.append(" ").append(WildFlySecurityManager.getPropertyPrivileged("cli.jvm.args", null));
        }
        WildFlySecurityManager.setPropertyPrivileged("jvm.args", sb.toString());
        container.start(null, null, Server.StartMode.ADMIN_ONLY, System.out, false, null, null, null, null, new Path[]{testYaml});
        container.waitForLiveServerToReload(TimeoutUtil.adjust(5000));
        waitForRunningMode("NORMAL");
        String xml = readConfigAsXml();
        compareXML(expectedBootCLiXml, xml);
    }

    @Test
    public void testYamlChangesAppliedInAdminOnlyModeWithoutBootCliScript() throws Exception {
        container.start(null, null, Server.StartMode.ADMIN_ONLY, System.out, false, null, null, null, null, new Path[]{testYaml});
        Assert.assertEquals("Yaml changes to configuration were persisted to xml. This should never happen as it's in read-only mode.", expectedXml, readConfigAsXml());
    }

    @Test
    public void testNoYaml() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{});
            Assert.fail("Server start must fail if no yaml is specified.");
        } catch (Exception ex) {
            String output = byteArrayOutputStream.toString();
            Assert.assertTrue("If no yaml is set then server must fail with FATAL error. But there is no such a log entry." + System.lineSeparator() + output,
                    output.lines().anyMatch(
                            line -> line.contains("FATAL [org.jboss.as.server] (main) WFLYSRV0239: Aborting with exit code 1") || line.contains("WFLYSRV0072: Value expected for option --yaml")));
        }
    }

    @Test
    public void testRemoveAndAddingResourceWithOverridingAttributesByTwoYamlFiles() throws Exception {
        container.startYamlExtension(new Path[]{testYaml, testRemoveSocketYaml, testSocketOverrideYaml});
        // read model and verify that expected changes are there
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        ModelNode outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo-override", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8083", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2-override", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8084", outboundSocketBindings.get("foo2").get("port").asString());
    }

    @Test
    public void testAddingResourceWithOverridingAttributesByTwoYamlFiles() throws Exception {
        container.startYamlExtension(new Path[]{testYaml, testSocketOverrideYaml});
        // read model and verify that expected changes are there
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        ModelNode outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo-override", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8083", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2-override", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8084", outboundSocketBindings.get("foo2").get("port").asString());
    }

    @Test
    public void testAttributeOverrideByTwoYamlFiles() throws Exception {
        container.startYamlExtension(new Path[]{testYaml, testPortOffsetOverrideYaml});
        // read model and verify that expected changes are there
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set port-offset is wrong", "0", result.get("port-offset").asString());
    }

    @Test
    public void testReplacingResourceByEmptyResourceLogsWarning() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testReplacingByEmptyResourceYaml});
        String expectedConsoleOutput = "WARN  [org.jboss.as.controller.management-operation] (Controller Boot Thread) WFLYCTL0490: A YAML resource has been defined for the address /subsystem=logging/periodic-rotating-file-handler=FILE without any attribute. No actions will be taken.";
        assertThat("If resource exists and is replaced by empty resource then warning must be logged. But there is none: " + byteArrayOutputStream, byteArrayOutputStream.toString(), CoreMatchers.containsString(expectedConsoleOutput));
    }

    @Test
    public void testStartWithBadlyIndentedYaml() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testWrongIndentationYaml});
            Assert.fail("Server must not start with badly format yaml.");
        } catch (RuntimeException ex) {
            Assert.assertFalse("Server must not start with badly format yaml.", container.isStarted());
            String expectedConsoleOutput = "(?s).*mapping values are not allowed here.* in 'reader', line \\d+, column \\d+.*port-offset: \\$\\{jboss\\.socket\\.binding\\.port-of.*";
            Assert.assertTrue("Server must provide useful information where yaml is wrong: " + byteArrayOutputStream, byteArrayOutputStream.toString().matches(expectedConsoleOutput));
        }
    }

    @Test
    public void testStartWithNonExistentAttributeYaml() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testNonExistentAttributeYaml});
            Assert.fail("Server must not start with non-existent attribute.");
        } catch (RuntimeException ex) {
            Assert.assertFalse("Server must not start with non-existent attribute: " + byteArrayOutputStream, container.isStarted());
            String expectedConsoleOutput = "ERROR [org.jboss.as.server] (Controller Boot Thread) WFLYSRV0055: Caught exception during boot: java.lang.IllegalArgumentException: "
                    + "WFLYCTL0509: No attribute called 'non-existent-attribute' is defined at address '/socket-binding-group=standard-sockets'.";
            assertThat("Server log must contain ERROR with information which attribute is wrong: " + byteArrayOutputStream, byteArrayOutputStream.toString(), CoreMatchers.containsString(expectedConsoleOutput));
        }
    }

    @Test
    public void testYamlOperations() throws Exception {
        container.startYamlExtension(new Path[]{testOperationsYaml});
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml operation to remove socket binding was not executed. Socket binding is still present.", "undefined", result.get("socket-binding").get("management-https").asString());
        result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("subsystem", "elytron").toModelNode(), true)));
        Assert.assertEquals("Yaml operation to undefine disallowed-providers was not executed.", "undefined", result.get("disallowed-providers").asString());
        ModelNode permissions = result.get("permission-set").get("default-permissions").get("permissions");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "[{\"class-name\" => \"org.wildfly.security.auth.permission.LoginPermission\",\"module\" => \"org.wildfly.security.elytron-base\",\"target-name\" => \"*\"}]", permissions.asString());
    }

    @Test
    public void testUndefineNonExistentAttributeYamlOperations() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testUndefineNonExistentAttributeYamlOperations});
            Assert.fail("Server must not start with non-existent attribute.");
        } catch (RuntimeException ex) {
            Assert.assertFalse("Server must not start with non-existent attribute: " + byteArrayOutputStream, container.isStarted());
            String expectedConsoleOutput = "ERROR [org.jboss.as.controller.management-operation] (Controller Boot Thread) WFLYCTL0013: Operation (\"undefine-attribute\") failed - address: ([\n"
                    + "    (\"socket-binding-group\" => \"standard-sockets\"),\n"
                    + "    (\"socket-binding\" => \"txn-status-manager\")\n"
                    + "]) - failure description: \"WFLYCTL0201: Unknown attribute 'asfds'\"";
            assertThat("Server log must contain ERROR with information which attribute is wrong: " + byteArrayOutputStream, byteArrayOutputStream.toString(), CoreMatchers.containsString(expectedConsoleOutput));
        }
    }

    @Test
    public void testRemoveAttributeRemovesAboveResourceYamlOperations() throws Exception {
        // removing attribute on resource actually removes the whole resource
        container.startYamlExtension(new Path[]{testRemoveAttributeRemovesAboveResourceYamlOperations});
        Assert.assertTrue("Server must start.", container.isStarted());
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Socket binding management-https must be removed.", "undefined", result.get("socket-binding").get("management-https").asString());
    }

    @Test
    public void testRemoveNonExistentResource() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testRemoveNonExistentResource});
        Assert.assertTrue("Server must start: " + byteArrayOutputStream, container.isStarted());
        String expectedConsoleOutput = "WARN  [org.jboss.as.controller.management-operation] (Controller Boot Thread) WFLYCTL0512: No resource exists at address '/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=non-existent-binding'. Ignoring the remove opreation.";
        assertThat("Server log must contain WARN with information what was wrong: " + byteArrayOutputStream, byteArrayOutputStream.toString(), CoreMatchers.containsString(expectedConsoleOutput));
    }

    @Test
    public void testListAddOperationToStringFails() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testListAddOperationToStringFails});
            Assert.fail("Server must not start.");
        } catch (Exception ex) {
            String expectedConsoleOutput = "ERROR [org.jboss.as.server] (Controller Boot Thread) WFLYSRV0055: Caught exception during boot: java.lang.IllegalArgumentException: WFLYCTL0510: No operation list-add can be executed for attribute called 'level' is defined at address '/subsystem=logging/root-logger=ROOT'.";
            assertThat("Server log must contain ERROR with information what was wrong: " + byteArrayOutputStream, byteArrayOutputStream.toString(), CoreMatchers.containsString(expectedConsoleOutput));
        }
    }

    @Test
    public void testListAddOperationToNonExistentResourceFails() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            container.startYamlExtension(new PrintStream(byteArrayOutputStream), new Path[]{testListAddOperationToNonExistentResourceFails});
            Assert.fail("Server must not start.");
        } catch (Exception ex) {
            String expectedConsoleOutput = "ERROR [org.jboss.as.server] (Controller Boot Thread) WFLYSRV0055: Caught exception during boot: java.lang.IllegalArgumentException: WFLYCTL0510: No operation list-add can be executed for attribute called 'NON-EXISTENT' is defined at address '/subsystem=logging/root-logger=NON-EXISTENT'.";
            assertThat("Server log must contain ERROR with information what was wrong: " + byteArrayOutputStream, byteArrayOutputStream.toString(), CoreMatchers.containsString(expectedConsoleOutput));
        }
    }

    @Test
    public void testReadConfigAsXmlFile() throws Exception {
        container.startYamlExtension(new Path[]{testYaml});
        ModelNode request = new ModelNode();
        request.get("operation").set("read-config-as-xml-file");
        request.get("address").setEmptyList();
        OperationResponse response = container.getClient().getControllerClient().executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        Assert.assertEquals(1, response.getInputStreams().size());
        Assert.assertEquals(SUCCESS, response.getResponseNode().require(OUTCOME).asString());
        String uuid = response.getResponseNode().require(RESULT).require(UUID).asStringOrNull();
        Assert.assertNotNull(uuid);
        OperationResponse.StreamEntry stream = response.getInputStream(uuid);
        Assert.assertNotNull(stream);
        Assert.assertEquals("application/xml", stream.getMimeType());
        String xml;
        try (InputStream in = stream.getStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            IOUtils.copy(in, out);
            xml = out.toString(StandardCharsets.UTF_8);
        }
        Path standalone = Paths.get(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "."), "standalone", "configuration", "standalone.xml");
        String expectedXml = new String(Files.readAllBytes(standalone));
        Assert.assertNotEquals("XML configs must not be the same as yaml changes are not persisted." + standalone, expectedXml, xml);
    }

    @Test
    public void testIdempotence() throws Exception {
        container.startYamlExtension(new Path[]{testYaml, testYaml});
        Assert.assertEquals("Yaml changes to configuration were persisted to xml. This should never happen as it's in read-only mode.", expectedXml, readConfigAsXml());

        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        ModelNode outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8081", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8082", outboundSocketBindings.get("foo2").get("port").asString());
    }

    @Test
    public void testYamlChangesSurviveReload() throws Exception {
        container.startYamlExtension(new Path[]{testYaml});
        Assert.assertEquals("Yaml changes to configuration were persisted to xml. This should never happen as it's in read-only mode.", expectedXml, readConfigAsXml());
        // read model and verify that test.yml changes are there
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        ModelNode outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8081", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8082", outboundSocketBindings.get("foo2").get("port").asString());

        // reload and check that all changes are still there
        container.reload();

        result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8081", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8082", outboundSocketBindings.get("foo2").get("port").asString());
        // reload and check that all changes are still there
        container.reload(Server.StartMode.ADMIN_ONLY);

        result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8081", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8082", outboundSocketBindings.get("foo2").get("port").asString());
    }

    @Test
    public void testPostStartCLIChangesToModelSurviveReload() throws Exception {
        container.startYamlExtension(new Path[]{testYaml});
        Assert.assertEquals("Yaml changes to configuration were persisted to xml. This should never happen as it's in read-only mode.", expectedXml, readConfigAsXml());
        // read model and verify that test.yml changes are there
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        ModelNode outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8081", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8082", outboundSocketBindings.get("foo2").get("port").asString());

        // CLI change to yaml changed part of config
        // CLI change to non-yaml changed part of config
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine("/socket-binding-group=standard-sockets:write-attribute(name=port-offset, value=0)", false);
            cli.sendLine("/subsystem=logging/:write-attribute(name=use-deployment-logging-config, value=false)", false);
        }

        // reload and check that all changes are still there
        container.reload();

        result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Change set to port-offset is wrong", "0", result.get("port-offset").asString());
        result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("subsystem", "logging").toModelNode(), true)));
        Assert.assertEquals("Change set to use-deployment-logging-config is wrong", "false", result.get("use-deployment-logging-config").asString());
    }


    @Test
    public void testPostStartCLIChangesToModelDoNotSurviveRestart() throws Exception {
        container.startYamlExtension(new Path[]{testYaml});
        Assert.assertEquals("Yaml changes to configuration were persisted to xml. This should never happen as it's in read-only mode.", expectedXml, readConfigAsXml());
        // read model and verify that test.yml changes are there
        ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set default interface is wrong", "public", result.get("default-interface").asString());
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        ModelNode outboundSocketBindings = result.get("remote-destination-outbound-socket-binding");
        Assert.assertEquals("Yaml change to port set mail-smtp outbound socket binding is wrong", "foo", outboundSocketBindings.get("mail-snmt").get("host").asString());
        Assert.assertEquals("Yaml change to host set mail-smtp outbound socket binding is wrong", "8081", outboundSocketBindings.get("mail-snmt").get("port").asString());
        Assert.assertEquals("Yaml change to port set foo2 outbound socket binding is wrong", "foo2", outboundSocketBindings.get("foo2").get("host").asString());
        Assert.assertEquals("Yaml change to host set foo2 outbound socket binding is wrong", "8082", outboundSocketBindings.get("foo2").get("port").asString());

        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine("/socket-binding-group=standard-sockets:write-attribute(name=port-offset, value=0)", false);
            cli.sendLine("/subsystem=logging/:write-attribute(name=use-deployment-logging-config, value=false)", false);
        }

        // restart and check that changes are NOT there
        container.stop();
        container.startYamlExtension(new Path[]{testYaml});

        result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("socket-binding-group", "standard-sockets").toModelNode(), true)));
        Assert.assertEquals("Yaml change to set port-offset is wrong", "${jboss.socket.binding.port-offset:0}", result.get("port-offset").asString());
        result = Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("subsystem", "logging").toModelNode(), true)));
        Assert.assertEquals("Change set to use-deployment-logging-config is wrong", "true", result.get("use-deployment-logging-config").asString());

        // restart server without yaml changes and check that xml config did no change
        container.stop();
        container.start();

        Assert.assertEquals("Yaml changes to configuration were persisted to xml. This should never happen.", defaultXml, readConfigAsXml());
    }

    private void waitForRunningMode(String runningMode) throws Exception {
        // Following a reload to normal mode, we might read the running mode too early and hit the admin-only server
        // Cycle around a bit to make sure we get the server reloaded into normal mode
        long end = System.currentTimeMillis() + TimeoutUtil.adjust(10000);
        while (true) {
            try {
                Thread.sleep(100);
                Assert.assertEquals(runningMode, getRunningMode());
                break;
            } catch (Throwable e) {
                if (System.currentTimeMillis() >= end) {
                    throw e;
                }
            }
        }
    }

    String getRunningMode() throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.EMPTY_ADDRESS, RUNNING_MODE);
        ModelNode result = container.getClient().executeForResult(op);
        return result.asString();
    }

    private void compareXML(String expected, String result) {
        String[] expectedLines = expected.split(System.lineSeparator());
        String[] resultLines = result.split(System.lineSeparator());
        for (int i = 0; i < expectedLines.length; i++) {
            if (i < resultLines.length) {
                Assert.assertEquals("Expected " + expectedLines[i] + " but got " + resultLines[i] + " in " + System.lineSeparator() + result, removeWhiteSpaces(expectedLines[i]), removeWhiteSpaces(resultLines[i]));
            } else {
                Assert.fail("Missing line " + expectedLines[i] + " in " + System.lineSeparator() + result);
            }
        }
    }

    private ModelNode readDeployment(ModelControllerClient client, String deploymentName) throws IOException {
        return Operations.readResult(client.execute(Operations.createReadResourceOperation(PathAddress.pathAddress("deployment", deploymentName).toModelNode())));
    }

    private String readConfigAsXml() throws IOException {
        ModelControllerClient client = container.getClient().getControllerClient();
        return removeWhiteSpaces(Operations.readResult(client.execute(READ_CONFIG)).asString().replace("\\\"", "\"").replace("\r\n", "\n"));
    }

    private static String removeWhiteSpaces(String line) {
        return Pattern.compile("(^\\s*$\\r?\\n)+", Pattern.MULTILINE).matcher(line.stripTrailing()).replaceAll("");
    }
}
