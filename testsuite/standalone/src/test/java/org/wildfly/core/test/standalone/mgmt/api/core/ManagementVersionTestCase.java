/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt.api.core;

import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

/**
 * Test for functionality added with AS7-2234.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@RunWith(WildFlyRunner.class)
public class ManagementVersionTestCase extends ContainerResourceMgmtTestBase {



    @Test
    public void testRootResource() throws Exception  {
        ModelNode op = createOpNode(null, "read-resource");

        ModelNode result = executeOperation(op);
        ModelNode major = result.get("management-major-version");
        ModelNode minor = result.get("management-minor-version");
        ModelNode micro = result.get("management-micro-version");
        Assert.assertEquals(ModelType.INT, major.getType());
        Assert.assertEquals(ModelType.INT, minor.getType());
        Assert.assertEquals(Version.MANAGEMENT_MAJOR_VERSION, major.asInt());
        Assert.assertEquals(Version.MANAGEMENT_MINOR_VERSION, minor.asInt());
        Assert.assertEquals(Version.MANAGEMENT_MICRO_VERSION, micro.asInt());
    }

    @Test
    public void testExtensions() throws Exception {
        ModelNode op = createOpNode(null, "read-children-resources");
        op.get("child-type").set("extension");
        op.get("recursive").set(true);
        op.get("include-runtime").set(true);

        ModelNode result = executeOperation(op);
        for (Property extension : result.asPropertyList()) {
            String extensionName = extension.getName();
            ModelNode subsystems = extension.getValue().get("subsystem");
            Assert.assertEquals(extensionName + " has no subsystems", ModelType.OBJECT, subsystems.getType());
            for (Property subsystem : subsystems.asPropertyList()) {
                String subsystemName = subsystem.getName();
                ModelNode value = subsystem.getValue();
                Assert.assertEquals(subsystemName + " has major version", ModelType.INT, value.get("management-major-version").getType());
                Assert.assertEquals(subsystemName + " has minor version", ModelType.INT, value.get("management-minor-version").getType());
                Assert.assertEquals(subsystemName + " has micro version", ModelType.INT, value.get("management-micro-version").getType());
                Assert.assertEquals(subsystemName + " has namespaces", ModelType.LIST, value.get("xml-namespaces").getType());
                Assert.assertTrue(subsystemName + " has positive major version", value.get("management-major-version").asInt() > 0);
                Assert.assertTrue(subsystemName + " has positive minor version", value.get("management-minor-version").asInt() >= 0);
                Assert.assertTrue(subsystemName + " has positive micro version", value.get("management-micro-version").asInt() >= 0);
                Assert.assertTrue(subsystemName + " has more than zero namespaces", value.get("xml-namespaces").asInt() > 0);
            }
        }
    }
}
