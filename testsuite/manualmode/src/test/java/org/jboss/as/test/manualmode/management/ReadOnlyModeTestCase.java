/*
 * Copyright 2018 JBoss by Red Hat.
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
package org.jboss.as.test.manualmode.management;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.repository.PathUtil;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ReadOnlyModeTestCase {

    @Inject
    private ServerController container;

    @Test
    public void testConfigurationNotUpdated() throws Exception {
        container.startReadOnly();

        ModelNode address = PathAddress.pathAddress("system-property", "read-only").toModelNode();
        try (ModelControllerClient client = container.getClient().getControllerClient()) {
            ModelNode op = Operations.createAddOperation(address);
            op.get("value").set(true);
            CoreUtils.applyUpdate(op, client);
            Assert.assertTrue(Operations.readResult(client.execute(Operations.createReadAttributeOperation(address, "value"))).asBoolean());
            container.reload();
            Assert.assertTrue(Operations.readResult(client.execute(Operations.createReadAttributeOperation(address, "value"))).asBoolean());
        }

        container.stop();
        container.startReadOnly();
        try (ModelControllerClient client = container.getClient().getControllerClient()) {
            Assert.assertTrue(Operations.getFailureDescription(client.execute(Operations.createReadAttributeOperation(address, "value"))).asString().contains("WFLYCTL0216"));
        }
    }

    @Test
    public void testReadOnlyConfigurationDirectory() throws Exception {
        // We ignore the test on Windows to prevent in case of errors the pollution of %TMPDIR% with read only directories
        // On unix machines, the /tmp dir is always deleted on each server boot by root user.
        Assume.assumeFalse(TestSuiteEnvironment.isWindows());

        final Path jbossHome = Paths.get(System.getProperty("jboss.home"));
        final Path configDir = jbossHome.resolve("standalone").resolve("configuration");
        final Path standaloneTmpDir = jbossHome.resolve("standalone").resolve("tmp");
        final Path osTmpDir =  Paths.get("/tmp");
        final Path roConfigDir = Files.createTempDirectory(osTmpDir, "wildfly-test-suite-");

        PathUtil.copyRecursively(configDir, roConfigDir, true);

        Set<PosixFilePermission> perms = new HashSet<>();

        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);

        Files.getFileAttributeView(roConfigDir, PosixFileAttributeView.class).setPermissions(perms);

        assertFalse(roConfigDir.toString() + " is writeable", Files.isWritable(roConfigDir));

        try {
            container.startReadOnly(roConfigDir);
            assertTrue("standalone_xml_history not found in " + standaloneTmpDir.toString(), Files.exists(standaloneTmpDir.resolve("standalone_xml_history")));

            ModelNode result;
            PathAddress address = PathAddress.pathAddress(PathElement.pathElement("subsystem", "elytron")).append("security-domain", "ApplicationDomain");
            try (ModelControllerClient client = container.getClient().getControllerClient()) {
                ModelNode op = Operations.createWriteAttributeOperation(address.toModelNode(), "security-event-listener", "local-audit");
                result = client.execute(op);

                Assert.assertTrue("Operation " + op.toString() + " failed with result " + result.toString(), Operations.isSuccessfulOutcome(result));
                Assert.assertTrue("Server it is expected to be in reload-required.", result.get("response-headers").get("process-state").asString().equals("reload-required"));

                container.reload();
                result = Operations.readResult(client.execute(Operations.createReadAttributeOperation(address.toModelNode(), "security-event-listener")));
                Assert.assertTrue("'security-event-listener' is expected to be 'local-audit'", result.asString().equals("local-audit"));
            }

            container.stop();
            container.startReadOnly(roConfigDir);
            try (ModelControllerClient client = container.getClient().getControllerClient()) {
                result = Operations.readResult(client.execute(Operations.createReadAttributeOperation(address.toModelNode(), "security-event-listener")));
                Assert.assertTrue("'security-event-listener' is expected to be 'undefined'", result.asString().equals("undefined"));
            }

        } finally {
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.GROUP_WRITE);
            perms.add(PosixFilePermission.OTHERS_WRITE);

            Files.getFileAttributeView(roConfigDir, PosixFileAttributeView.class).setPermissions(perms);

            PathUtil.deleteRecursively(roConfigDir);
        }
    }

    @After
    public void stopContainer() {
        container.stop();
    }
}
