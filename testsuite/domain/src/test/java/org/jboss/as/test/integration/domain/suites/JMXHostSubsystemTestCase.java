/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.jmx.model.ModelControllerMBeanHelper;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class JMXHostSubsystemTestCase {

    private static final String RESOLVED_DOMAIN = "jboss.as";

    static final ObjectName RESOLVED_MODEL_FILTER = createObjectName(RESOLVED_DOMAIN  + ":*");
    static final ObjectName RESOLVED_ROOT_MODEL_NAME = ModelControllerMBeanHelper.createRootObjectName(RESOLVED_DOMAIN);


    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;
    private DomainClient primaryClient;
    private DomainClient secondaryClient;
    JMXConnector primaryConnector;
    MBeanServerConnection primaryConnection;
    JMXConnector secondaryConnector;
    MBeanServerConnection secondaryConnection;


    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(JMXHostSubsystemTestCase.class.getSimpleName());
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


    @Before
    public void initialize() throws Exception {
        primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        primaryConnector = setupAndGetConnector(domainPrimaryLifecycleUtil);
        primaryConnection = primaryConnector.getMBeanServerConnection();
        secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
        secondaryConnector = setupAndGetConnector(domainSecondaryLifecycleUtil);
        secondaryConnection = secondaryConnector.getMBeanServerConnection();
    }

    @After
    public void closeConnection() throws Exception {
        IoUtils.safeClose(primaryConnector);
        IoUtils.safeClose(secondaryConnector);
    }

    /**
     * Test that all the MBean infos can be read properly
     */
    @Test
    public void testAllMBeanInfosPrimary() throws Exception {
        testAllMBeanInfos(primaryConnection);
    }

    /**
     * Test that all the MBean infos can be read properly
     */
    @Test
    public void testAllMBeanInfosSecondary() throws Exception {
        testAllMBeanInfos(secondaryConnection);
    }

    private void testAllMBeanInfos(MBeanServerConnection connection) throws Exception {
        Set<ObjectName> names = connection.queryNames(RESOLVED_MODEL_FILTER, null);
        Map<ObjectName, Exception> failedInfos = new HashMap<ObjectName, Exception>();

        for (ObjectName name : names) {
            try {
                Assert.assertNotNull(connection.getMBeanInfo(name));
            } catch (Exception e) {
                System.out.println("Error getting info for " + name);
                failedInfos.put(name, e);
            }
        }
        Assert.assertTrue(failedInfos.toString(), failedInfos.isEmpty());
    }

    @Test
    public void testSystemPropertiesPrimary() throws Exception {
        //testDomainModelSystemProperties(primaryClient, true, primaryConnection);
        //For now disable writes on the primary while we decide if it is a good idea or not
        testDomainModelSystemProperties(primaryClient, false, primaryConnection);
    }

    @Test
    public void testSystemPropertiesSecondary() throws Exception {
        testDomainModelSystemProperties(secondaryClient, false, secondaryConnection);
    }

    private void testDomainModelSystemProperties(DomainClient client, boolean primary, MBeanServerConnection connection) throws Exception {
        String[] initialNames = getSystemPropertyNames(client);

        ObjectName testName = new ObjectName(RESOLVED_DOMAIN + ":system-property=mbeantest");
        assertNoMBean(testName, connection);

        MBeanInfo info = connection.getMBeanInfo(RESOLVED_ROOT_MODEL_NAME);
        MBeanOperationInfo opInfo = null;
        for (MBeanOperationInfo op : info.getOperations()) {
            if (op.getName().equals("addSystemProperty")) {
                Assert.assertNull(opInfo); //Simple check to guard against the op being overloaded
                opInfo = op;

            }
        }
        Assert.assertEquals(primary, opInfo != null);

        try {
            connection.invoke(RESOLVED_ROOT_MODEL_NAME, "addSystemProperty", new Object[] {"mbeantest", false, "800"}, new String[] {String.class.getName(), Boolean.class.getName(), String.class.getName()});
            Assert.assertTrue(primary);//The invoke should not work if it is a secondary since the domain model is only writable from the secondary
        } catch (Exception e) {
            if (primary) {
                //There should be no exception executing the invoke from a primary HC
                throw e;
            }
            //Expected for a secondary; we can't do any more
            return;
        }
        try {
            String[] newNames = getSystemPropertyNames(client);
            Assert.assertEquals(initialNames.length + 1, newNames.length);
            boolean found = false;
            for (String s : newNames) {
                if (s.equals("mbeantest")) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue(found);
            Assert.assertNotNull(connection.getMBeanInfo(new ObjectName(RESOLVED_DOMAIN + ":system-property=mbeantest")));
        } finally {
            connection.invoke(new ObjectName(RESOLVED_DOMAIN + ":system-property=mbeantest"), "remove", new Object[0], new String[0]);
        }

        assertNoMBean(testName, connection);

        Assert.assertEquals(initialNames.length, getSystemPropertyNames(client).length);
    }

    @Test
    public void testNoSecondaryMBeansVisibleFromPrimary() throws Exception {
        testNoMBeansVisible(primaryConnection, true);
    }

    @Test
    public void testNoPrimaryMBeansVisibleFromSecondary() throws Exception {
        testNoMBeansVisible(secondaryConnection, false);
    }

    private void testNoMBeansVisible(MBeanServerConnection connection, boolean primary) throws Exception {
        String pattern = "jboss.as:host=%s,extension=org.jboss.as.jmx";
        ObjectName mine = createObjectName(String.format(pattern, primary ? "primary" : "secondary"));
        ObjectName other = createObjectName(String.format(pattern, primary ? "secondary" : "primary"));
        assertNoMBean(other, connection);
        Assert.assertNotNull(connection.getMBeanInfo(mine));
    }

    private void assertNoMBean(ObjectName name, MBeanServerConnection connection) throws Exception {
        try {
            connection.getMBeanInfo(name);
            Assert.fail("Should not have found mbean with nane " + name);
        } catch (InstanceNotFoundException expected) {
        }
    }

    private String[] getSystemPropertyNames(DomainClient client) throws Exception {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        op.get(OP_ADDR).setEmptyList();
        op.get(CHILD_TYPE).set("system-property");

        ModelNode result = client.execute(op);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
        List<ModelNode> propertyNames = result.get(RESULT).asList();
        String[] names = new String[propertyNames.size()];
        int i = 0;
        for (ModelNode node : propertyNames) {
            names[i++] = node.asString();
        }
        return names;
    }


    private static ObjectName createObjectName(String name) {
        try {
            return ObjectName.getInstance(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JMXConnector setupAndGetConnector(DomainLifecycleUtil util) throws Exception {
        WildFlyManagedConfiguration config = util.getConfiguration();
        // Make sure that we can connect to the MBean server
        String urlString = System
                .getProperty("jmx.service.url", "service:jmx:remoting-jmx://" + NetworkUtils.formatPossibleIpv6Address(config.getHostControllerManagementAddress()) + ":" + config.getHostControllerManagementPort());
        JMXServiceURL serviceURL = new JMXServiceURL(urlString);
        return JMXConnectorFactory.connect(serviceURL, null);
    }
}
