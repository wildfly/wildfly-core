/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.persistence;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_MODE;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import jakarta.inject.Inject;
import java.util.Iterator;
import java.util.regex.Pattern;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
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

    private static Path markerDirectory;
    private static Path testYaml;
    private static Path cliScript;
    private static String expectedXml;
    private static String expectedBootCLiXml;
    private static String originalJvmArgs;

    @BeforeClass
    public static void setup() throws Exception {
        Assume.assumeTrue("Layer testing provides a different XML file than the standard one which results in failures", System.getProperty("ts.layers") == null);
        testYaml = new File(YamlExtensionTestCase.class.getResource("test.yml").toURI()).toPath().toAbsolutePath();
        cliScript = new File(YamlExtensionTestCase.class.getResource("test.cli").toURI()).toPath().toAbsolutePath();
        expectedXml = loadFile(new File(YamlExtensionTestCase.class.getResource("test.xml").toURI()).toPath());
        expectedBootCLiXml = loadFile(new File(YamlExtensionTestCase.class.getResource("testWithCli.xml").toURI()).toPath());
        originalJvmArgs = WildFlySecurityManager.getPropertyPrivileged("jvm.args", null);
        Path target = new File("target").toPath();
        markerDirectory = Files.createDirectories(target.resolve("yaml").resolve("cli-boot-ops"));
        Files.copy(new File(YamlExtensionTestCase.class.getResource("bootable-groups.properties").toURI()).toPath(),
                target.resolve(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "toto")).resolve("standalone").resolve("configuration").resolve("bootable-groups.properties"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(new File(YamlExtensionTestCase.class.getResource("bootable-users.properties").toURI()).toPath(),
                target.resolve(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "toto")).resolve("standalone").resolve("configuration").resolve("bootable-users.properties"), StandardCopyOption.REPLACE_EXISTING);
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

    private String readXmlConfig() throws IOException {
        try (ModelControllerClient client = container.getClient().getControllerClient()) {
            return removeWhiteSpaces(Operations.readResult(client.execute(READ_CONFIG)).asString().replace("\\\"", "\"").replace("\r\n", "\n"));
        }
    }

    private static String removeWhiteSpaces(String line) {
        return Pattern.compile ("(^\\s*$\\r?\\n)+", Pattern.MULTILINE).matcher(line.stripTrailing()).replaceAll("");
    }
}
