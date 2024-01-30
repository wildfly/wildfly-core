/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.persistence;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_MODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.regex.Pattern;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
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

    private static final ModelNode READ_CONFIG = Util.createEmptyOperation("read-config-as-xml", PathAddress.EMPTY_ADDRESS);

    @Inject
    private ServerController container;

    private static final Path basedir = new File(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "toto")).toPath().resolve("standalone");
    private static Path markerDirectory;
    private static Path testYaml;
    private static Path testDeploymentYaml;
    private static Path testManagedDeploymentYaml;
    private static Path cliScript;
    private static String expectedXml;
    private static String expectedBootCLiXml;
    private static String originalJvmArgs;

    @BeforeClass
    public static void setup() throws Exception {
        Assume.assumeTrue("Layer testing provides a different XML file than the standard one which results in failures", System.getProperty("ts.layers") == null);
        Path configurationDir = basedir.resolve("configuration");
        Path referenceConfiguration = configurationDir.resolve("reference-standalone.xml");
        Files.copy(configurationDir.resolve("standalone.xml"), referenceConfiguration, REPLACE_EXISTING);
        try (CLIWrapper cli = new CLIWrapper(false)) {
            cli.sendLine("embed-server --admin-only --server-config=reference-standalone.xml");
            cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=http:add(interface=public)");
            cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=https:add()");
            cli.sendLine("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=mail-snmt:add(host=foo, port=8081)");
            cli.sendLine("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=foo2:add(host=foo2, port=8082)");
            cli.quit();
        }
        Path referenceCliConfiguration = configurationDir.resolve("reference-cli-standalone.xml");
        Files.copy(configurationDir.resolve("standalone.xml"), referenceCliConfiguration, REPLACE_EXISTING);
        Files.copy(new File(YamlExtensionTestCase.class.getResource("bootable-groups.properties").toURI()).toPath(), configurationDir.resolve("bootable-groups.properties"), REPLACE_EXISTING);
        Files.copy(new File(YamlExtensionTestCase.class.getResource("bootable-users.properties").toURI()).toPath(), configurationDir.resolve("bootable-users.properties"), REPLACE_EXISTING);
        try (CLIWrapper cli = new CLIWrapper(false)) {
            cli.sendLine("embed-server --admin-only --server-config=reference-cli-standalone.xml");
            cli.sendLine("/system-property=foo:add(value=bar)");
            cli.sendLine("/subsystem=elytron/properties-realm=bootable-realm:add(users-properties={path=bootable-users.properties, plain-text=true, relative-to=jboss.server.config.dir}, groups-properties={path=bootable-groups.properties, relative-to=jboss.server.config.dir})");
            cli.sendLine("/subsystem=elytron/security-domain=BootableDomain:add(default-realm=bootable-realm, permission-mapper=default-permission-mapper, realms=[{realm=bootable-realm, role-decoder=groups-to-roles}])");
            cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=http:add(interface=public)");
            cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=https:add()");
            cli.sendLine("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=mail-snmt:add(host=foo, port=8081)");
            cli.sendLine("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=foo2:add(host=foo2, port=8082)");
            cli.quit();
        }
        testYaml = new File(YamlExtensionTestCase.class.getResource("test.yml").toURI()).toPath().toAbsolutePath();
        testDeploymentYaml = new File(YamlExtensionTestCase.class.getResource("test-deployment.yml").toURI()).toPath().toAbsolutePath();
        testManagedDeploymentYaml = new File(YamlExtensionTestCase.class.getResource("test-managed-deployment.yml").toURI()).toPath().toAbsolutePath();
        createDeployment(configurationDir.getParent().resolve("test.jar"));
        cliScript = new File(YamlExtensionTestCase.class.getResource("test.cli").toURI()).toPath().toAbsolutePath();
        expectedXml = loadFile(referenceConfiguration).replace("\"", "'");
        expectedBootCLiXml = loadFile(referenceCliConfiguration).replace("\"", "'");
        originalJvmArgs = WildFlySecurityManager.getPropertyPrivileged("jvm.args", null);
        Path target = new File("target").toPath();
        markerDirectory = Files.createDirectories(target.resolve("yaml").resolve("cli-boot-ops"));
        Files.copy(new File(YamlExtensionTestCase.class.getResource("bootable-groups.properties").toURI()).toPath(),
                target.resolve(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "toto")).resolve("standalone").resolve("configuration").resolve("bootable-groups.properties"), REPLACE_EXISTING);
        Files.copy(new File(YamlExtensionTestCase.class.getResource("bootable-users.properties").toURI()).toPath(),
                target.resolve(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "toto")).resolve("standalone").resolve("configuration").resolve("bootable-users.properties"), REPLACE_EXISTING);
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
    }

    @Test
    public void testSimpleYaml() throws URISyntaxException, Exception {
        try {
            container.startYamlExtension(new Path[]{testYaml});
            String xml = readXmlConfig();
            Assert.assertEquals(expectedXml, xml);
        } finally {
            container.stop();
        }
    }

    @Test
    public void testDeploymentYaml() throws URISyntaxException, Exception {
        try {
            container.startYamlExtension(new Path[]{testDeploymentYaml});
            try (ModelControllerClient client = container.getClient().getControllerClient()) {
                ModelNode deployment = readDeployment(client, "test.jar");
                Assert.assertEquals("test.jar", deployment.get(NAME).asString());
                Assert.assertEquals("test.jar", deployment.get(RUNTIME_NAME).asString());
                ModelNode contentItemNode = deployment.get(CONTENT).get(0);
                Assert.assertEquals("test.jar", contentItemNode.get(PATH).asString());
                Assert.assertEquals("jboss.server.base.dir", contentItemNode.get(RELATIVE_TO).asString());
                Assert.assertEquals(true, contentItemNode.get(ARCHIVE).asBoolean());
                deployment = readDeployment(client, "hello.jar");
                Assert.assertEquals("hello.jar", deployment.get(NAME).asString());
                Assert.assertEquals("hello.jar", deployment.get(RUNTIME_NAME).asString());
                contentItemNode = deployment.get(CONTENT).get(0);
                Assert.assertEquals("test.jar", contentItemNode.get(PATH).asString());
                Assert.assertEquals("jboss.server.base.dir", contentItemNode.get(RELATIVE_TO).asString());
                Assert.assertEquals(true, contentItemNode.get(ARCHIVE).asBoolean());
            }
        } finally {
            container.stop();
        }
    }

    /**
     * Managed deployments are not supported. We should fail
     *
     * @throws URISyntaxException
     * @throws Exception
     */
    @Test
    public void testFailedDeploymentYaml() throws URISyntaxException, Exception {
        try {
            container.startYamlExtension(new Path[]{testManagedDeploymentYaml});
            Assert.assertFalse(container.isStarted());
        } catch (RuntimeException ex) {
            Assert.assertFalse(container.isStarted());
            try (final BufferedReader reader = Files.newBufferedReader(basedir.resolve("log").resolve("server.log"), StandardCharsets.UTF_8)) {
                Assert.assertTrue(reader.lines().anyMatch(line -> line.contains("WFLYCTL0505: Unsuported deployment yaml file hello.jar with attributes [empty]")));
            }
        } finally {
            container.stop();
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
    public void testSimpleYamlWithReload() throws URISyntaxException, Exception {
        try {
            container.startYamlExtension(new Path[]{testYaml});
            String xml = readXmlConfig();
            Assert.assertEquals(expectedXml, xml);
            container.waitForLiveServerToReload(TimeoutUtil.adjust(5000));
            xml = readXmlConfig();
            compareXML(expectedXml, xml);
        } finally {
            container.stop();
        }
    }

    @Test
    public void testSimpleYamlWithCliBootOps() throws URISyntaxException, Exception {
        try {
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
            String xml = readXmlConfig();
            compareXML(expectedBootCLiXml, xml);
        } finally {
            container.stop();
        }
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

    private String readXmlConfig() throws IOException {
        try (ModelControllerClient client = container.getClient().getControllerClient()) {
            return removeWhiteSpaces(Operations.readResult(client.execute(READ_CONFIG)).asString().replace("\\\"", "\"").replace("\r\n", "\n"));
        }
    }

    private static String removeWhiteSpaces(String line) {
        return Pattern.compile("(^\\s*$\\r?\\n)+", Pattern.MULTILINE).matcher(line.stripTrailing()).replaceAll("");
    }

}
