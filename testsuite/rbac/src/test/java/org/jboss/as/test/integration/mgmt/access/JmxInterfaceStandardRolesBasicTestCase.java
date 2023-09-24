/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PASSWORD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_USER;
import static org.junit.Assert.fail;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.ObjectName;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;

import org.jboss.as.test.integration.management.interfaces.JmxManagementInterface;
import org.jboss.as.test.integration.management.interfaces.ManagementInterface;
import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author jcechace
 * @author Ladislav Thon <lthon@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({StandardUsersSetupTask.class, StandardExtensionSetupTask.class})
public class JmxInterfaceStandardRolesBasicTestCase extends StandardRolesBasicTestCase {

    private static final String JMX_CONSTRAINED = "subsystem=rbac,rbac-constrained=jmx";
    private static final String HTTP_SOCKET_BINDING = "socket-binding-group=standard-sockets,socket-binding=management-http";

    @Override
    protected ManagementInterface createClient(String userName) {
        return JmxManagementInterface.create(
                getManagementClient().getRemoteJMXURL(),
                userName, RbacAdminCallbackHandler.STD_PASSWORD,
                getJmxDomain()
        );
    }

    @Before
    public void createResource() throws Exception {
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "rbac");
        ModelNode addResource = Util.createAddOperation(subsystemAddress.append("rbac-constrained", "jmx"));
        addResource.get("password").set("sa");
        addResource.get("security-domain").set("other");
        addResource.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        managementClient.getControllerClient().execute(addResource);
    }

    @After
    public void cleanResource() throws Exception {
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "rbac");
        ModelNode removeConstrained = Util.createRemoveOperation(subsystemAddress.append("rbac-constrained", "jmx"));
        removeConstrained.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        managementClient.getControllerClient().execute(removeConstrained);
    }

    protected String getJmxDomain() {
        return "jboss.as";
    }

    @Override
    public void testMonitor() throws Exception {
        super.testMonitor();

        ManagementInterface client = getClientForUser(MONITOR_USER);
        //checkAttributeAccessInfo(client, true, false);
        //checkSensitiveAttributeAccessInfo(client, false, false);
    }

    @Override
    public void testOperator() throws Exception {
        super.testOperator();

        ManagementInterface client = getClientForUser(MONITOR_USER);
        //checkAttributeAccessInfo(client, true, false);
        //checkSensitiveAttributeAccessInfo(client, false, false);
    }

    @Override
    public void testMaintainer() throws Exception {
        super.testMaintainer();

        ManagementInterface client = getClientForUser(MONITOR_USER);
        checkAttributeAccessInfo(client, true, true);
        //checkSensitiveAttributeAccessInfo(client, false, false);
    }

    @Override
    public void testDeployer() throws Exception {
        super.testDeployer();

        ManagementInterface client = getClientForUser(MONITOR_USER);
        //checkAttributeAccessInfo(client, true, false);
        //checkSensitiveAttributeAccessInfo(client, false, false);
    }

    @Override
    public void testAdministrator() throws Exception {
        super.testAdministrator();

        ManagementInterface client = getClientForUser(MONITOR_USER);
        checkAttributeAccessInfo(client, true, true);
        checkSensitiveAttributeAccessInfo(client, true, true);
    }

    @Override
    public void testAuditor() throws Exception {
        super.testAuditor();

        ManagementInterface client = getClientForUser(MONITOR_USER);
        //checkAttributeAccessInfo(client, true, false);
        //checkSensitiveAttributeAccessInfo(client, false, false);
    }

    @Override
    public void testSuperUser() throws Exception {
        super.testSuperUser();

        ManagementInterface client = getClientForUser(MONITOR_USER);
        checkAttributeAccessInfo(client, true, true);
        checkSensitiveAttributeAccessInfo(client, true, true);
    }

    // test utils
    // TODO check[Sensitive]AttributeAccessInfo calls are mostly commented out because of https://issues.jboss.org/browse/WFLY-1984
    private void checkAttributeAccessInfo(ManagementInterface client, boolean read, boolean write) throws Exception {
        JmxManagementInterface jmxClient = (JmxManagementInterface) client;
        readAttributeAccessInfo(jmxClient, HTTP_SOCKET_BINDING, PORT, read, write);
    }

    private void checkSensitiveAttributeAccessInfo(ManagementInterface client, boolean read, boolean write) throws Exception {
        JmxManagementInterface jmxClient = (JmxManagementInterface) client;
        readAttributeAccessInfo(jmxClient, JMX_CONSTRAINED, PASSWORD, read, write);
    }

    private void readAttributeAccessInfo(JmxManagementInterface client, String address, String attribute,
            boolean read, boolean write) throws Exception {
        ObjectName objectName = new ObjectName(getJmxDomain() + ":" + address);
        MBeanInfo mBeanInfo = client.getConnection().getMBeanInfo(objectName);
        for (MBeanAttributeInfo attrInfo : mBeanInfo.getAttributes()) {
            if (attrInfo.getName().equals(attribute)) {
                Assert.assertEquals(read, attrInfo.isReadable());
                Assert.assertEquals(write, attrInfo.isWritable());
                return;
            }
        }
        fail("Attribute " + attribute + " not found at " + address);
    }
}
