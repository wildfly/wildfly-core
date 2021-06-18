/*
 * Copyright 2021 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.management.persistence;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.Server;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class YamlExtensionTestCase {

    private static final ModelNode READ_CONFIG = Util.createEmptyOperation("read-config-as-xml", PathAddress.EMPTY_ADDRESS);

    @Inject
    private ServerController container;

    protected static Path markerDirectory;
    private static Path testYaml;
    private static Path cliScript;
    private static String expectedXml;
    private static String expectedBootCLiXml;
    private static String originalJvmArgs;

    @BeforeClass
    public static void setup() throws Exception {
        testYaml = new File(YamlExtensionTestCase.class.getResource("test.yml").toURI()).toPath().toAbsolutePath();
        cliScript = new File(YamlExtensionTestCase.class.getResource("test.cli").toURI()).toPath().toAbsolutePath();
        expectedXml = Files.readString(new File(YamlExtensionTestCase.class.getResource("test.xml").toURI()).toPath());
        expectedBootCLiXml = Files.readString(new File(YamlExtensionTestCase.class.getResource("testWithCli.xml").toURI()).toPath());
        originalJvmArgs = WildFlySecurityManager.getPropertyPrivileged("jvm.args", null);
        Path target = new File("target").toPath();
        markerDirectory = Files.createDirectories(target.resolve("yaml").resolve("cli-boot-ops"));
        Files.copy(new File(YamlExtensionTestCase.class.getResource("bootable-groups.properties").toURI()).toPath(),
                target.resolve(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "toto")).resolve("standalone").resolve("configuration").resolve("bootable-groups.properties"));
        Files.copy(new File(YamlExtensionTestCase.class.getResource("bootable-users.properties").toURI()).toPath(),
                target.resolve(WildFlySecurityManager.getPropertyPrivileged("jboss.home", "toto")).resolve("standalone").resolve("configuration").resolve("bootable-users.properties"));
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
            Assert.assertEquals(expectedXml, xml);
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
            String xml = readXmlConfig();
            System.out.println(xml);
            Assert.assertEquals(expectedBootCLiXml, xml);
            container.waitForLiveServerToReload(TimeoutUtil.adjust(5000));
            Assert.assertEquals(expectedBootCLiXml, xml);
        } finally {
            container.stop();
        }
    }

    private String readXmlConfig() throws IOException {
        try (ModelControllerClient client = container.getClient().getControllerClient()) {
            return Operations.readResult(client.execute(READ_CONFIG)).asString().replace("\\\"", "\"");
        }
    }
}
