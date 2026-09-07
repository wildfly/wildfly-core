/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.bootable.configurator;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOLVE_EXPRESSIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
public class BootableConfiguratorTestCase {
    private static final long RELOAD_WAITING_TIME = TimeoutUtil.adjust(10000);
    @Inject
    private ManagementClient managementClient;

    @Test
    public void test() throws Exception {
        String jbossHomeProp = System.getProperty("jboss.home");

        Path jbossHome = Paths.get(jbossHomeProp);
        Assert.assertTrue(Files.exists(jbossHome));

        Path configurators = jbossHome.resolve("modules/system/layers/base/org/wildfly/bootable-jar/main/bootable-configurators/bootable-configurators.properties");
        assertTrue(Files.exists(configurators));
        Properties p = new Properties();
        try (InputStream in = configurators.toFile().toURI().toURL().openStream()) {
            p.load(in);
            String module = p.getProperty("test-module");
            assertNotNull(module);
            Path modulePath = jbossHome.resolve("modules/system/layers/base/" + module.replaceAll("\\.", "/"));
            assertTrue(Files.exists(modulePath));
        }
        // Wait for the server to be in ready state
        long timeout = RELOAD_WAITING_TIME;
        int freq = 500;
        while(timeout > 0) {
            if (managementClient.isServerInNormalMode() && managementClient.isServerInRunningState()) {
                break;
            }
            timeout -= freq;
            Thread.sleep(freq);
        }
        if (!managementClient.isServerInNormalMode() && !managementClient.isServerInRunningState()) {
            throw new Exception("Server never reached the READY state");
        }
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(NAME).set(VALUE);
        op.get(RESOLVE_EXPRESSIONS).set("true");
        PathAddress address = PathAddress.pathAddress("system-property", "org.wildfly.core.test.bootable.configurator.property");
        op.get(OP_ADDR).set(address.toModelNode());
        ModelNode result = managementClient.getControllerClient().execute(op);
        String outcome = result.get(OUTCOME).asString();
        boolean bootableJAR = Boolean.getBoolean("wildfly.bootable.jar");

        // The configurator has been called
        if (bootableJAR) {
            assertEquals(SUCCESS, outcome);
            assertEquals("foo", result.get(RESULT).asString());
        } else {
            assertEquals(FAILED, outcome);
        }
    }
}
