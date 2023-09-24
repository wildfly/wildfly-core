/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.management.persistence;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.Server;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.jboss.as.domain.management.ModelDescriptionConstants.VALUE;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class EncodingPersistenceTestCase {
    static final PathAddress PROPERTY_ADDR = PathAddress.pathAddress(ModelDescriptionConstants.SYSTEM_PROPERTY, "test_property");
    @Inject
    private ServerController serverController;

    /**
     * Tests that we can write and read the persistence XML when the server is launched using an encoding different that UTF-8
     *
     * @throws Exception
     */
    @Test
    public void testEncodingManagementClient() throws Exception {
        final Path confDir = Paths.get(TestSuiteEnvironment.getJBossHome()).resolve("standalone").resolve("configuration");
        final Path origConfig = confDir.resolve("standalone.xml");
        final Path testConfig = confDir.resolve("encoding-standalone.xml");
        Files.copy(origConfig, testConfig);

        final String originalArgs = System.getProperty("jvm.args");
        try {
            System.setProperty("jvm.args", originalArgs + " -Dfile.encoding=windows-1250");
            serverController.start(testConfig.getFileName().toString(), Server.StartMode.NORMAL);

            final ManagementClient client = serverController.getClient();

            ModelNode op = Util.createAddOperation(PROPERTY_ADDR);
            op.get(ModelDescriptionConstants.VALUE).set("áéíóú");

            client.executeForResult(op);

            serverController.stop();
            serverController.start(testConfig.getFileName().toString(), Server.StartMode.NORMAL);

            op = Util.getReadResourceOperation(PROPERTY_ADDR);
            ModelNode result = client.executeForResult(op);
            Assert.assertEquals("áéíóú", result.get(VALUE).asString());

        } finally {
            System.setProperty("jvm.args", originalArgs);
            if (serverController.isStarted()) {
                serverController.stop();
            }
        }
    }

    /**
     * Verifies all the persistence XML files explicitly have an encoding header as UTF-8
     *
     * @throws IOException
     */
    @Test
    public void checkUtf8HeaderOnXmlConfigurationFiles() throws IOException {
        final Path jbossHome = Paths.get(TestSuiteEnvironment.getJBossHome());

        final Path standalone = jbossHome.resolve("standalone").resolve("configuration");
        try (Stream<Path> file = Files.list(standalone)) {
            file.filter(p -> p.toString().endsWith(".xml")).forEach(this::checkXmlHeader);
        }

        final Path domain = jbossHome.resolve("domain").resolve("configuration");
        if (Files.exists(domain)) {
            try (Stream<Path> file = Files.list(domain)) {
                file.filter(p -> p.toString().endsWith(".xml")).forEach(this::checkXmlHeader);
            }
        }

    }

    private void checkXmlHeader(Path f) {
        try {
            Assert.assertTrue("XML encoding header not found in " + f.toAbsolutePath(),
                    FileUtils.readFileToString(f.toFile(), Charset.forName("utf-8"))
                            .contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
