/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import java.io.IOException;

import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of various management operations involving core resources like system properties, paths, interfaces, socket bindings.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagementVersionTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ManagementVersionTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testDomainRootResource() throws Exception  {
        rootResourceTest(null, domainPrimaryLifecycleUtil.getDomainClient());
    }

    @Test
    public void testPrimaryHostRootResource() throws Exception  {
        rootResourceTest("host=primary", domainPrimaryLifecycleUtil.getDomainClient());
    }

    @Test
    public void testSecondaryHostRootResource() throws Exception  {
        rootResourceTest("host=secondary", domainSecondaryLifecycleUtil.getDomainClient());
    }

    @Test
    public void testDomainExtensions() throws Exception {
        extensionsTest(null, domainPrimaryLifecycleUtil.getDomainClient());
    }

    @Test
    public void testPrimaryServerExtensions() throws Exception {
        extensionsTest("host=primary/server=main-one", domainPrimaryLifecycleUtil.getDomainClient());
    }

    @Test
    public void testSecondaryServerExtensions() throws Exception {
        extensionsTest("host=secondary/server=other-two", domainSecondaryLifecycleUtil.getDomainClient());
    }

    private void rootResourceTest(String address, ModelControllerClient client) throws Exception {
        ModelNode op = createOpNode(address, "read-resource");

        ModelNode result = executeOperation(op, client);
        ModelNode major = result.get("management-major-version");
        ModelNode minor = result.get("management-minor-version");
        Assert.assertEquals(ModelType.INT, major.getType());
        Assert.assertEquals(ModelType.INT, minor.getType());
        Assert.assertEquals(Version.MANAGEMENT_MAJOR_VERSION, major.asInt());
        Assert.assertEquals(Version.MANAGEMENT_MINOR_VERSION, minor.asInt());
    }

    private void extensionsTest(String address, ModelControllerClient client) throws Exception {
        ModelNode op = createOpNode(address, "read-children-resources");
        op.get("child-type").set("extension");
        op.get("recursive").set(true);
        op.get("include-runtime").set(true);

        ModelNode result = executeOperation(op, client);
        for (Property extension : result.asPropertyList()) {
            String extensionName = extension.getName();
            ModelNode subsystems = extension.getValue().get("subsystem");
            Assert.assertEquals(extensionName + " has no subsystems", ModelType.OBJECT, subsystems.getType());
            for (Property subsystem : subsystems.asPropertyList()) {
                String subsystemName = subsystem.getName();
                ModelNode value = subsystem.getValue();
                Assert.assertEquals(subsystemName + " has major version", ModelType.INT, value.get("management-major-version").getType());
                Assert.assertEquals(subsystemName + " has minor version", ModelType.INT, value.get("management-minor-version").getType());
                Assert.assertEquals(subsystemName + " has namespaces", ModelType.LIST, value.get("xml-namespaces").getType());
                Assert.assertTrue(subsystemName + " has positive major version", value.get("management-major-version").asInt() > 0);
                Assert.assertTrue(subsystemName + " has non-negative minor version", value.get("management-minor-version").asInt() >= 0);
                Assert.assertTrue(subsystemName + " has more than zero namespaces", value.get("xml-namespaces").asInt() > 0);
            }
        }
    }

    private static ModelNode createOpNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        ModelNode list = op.get("address").setEmptyList();
        if (address != null) {
            String [] pathSegments = address.split("/");
            for (String segment : pathSegments) {
                String[] elements = segment.split("=");
                list.add(elements[0], elements[1]);
            }
        }
        op.get("operation").set(operation);
        return op;
    }

    private ModelNode executeOperation(final ModelNode op, final ModelControllerClient modelControllerClient) throws IOException, MgmtOperationException {
        return DomainTestUtils.executeForResult(op, modelControllerClient);
    }
}
