/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.rbac;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BASE_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.Attribute;
import javax.management.JMRuntimeException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.ArrayUtils;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.test.integration.domain.suites.FullRbacProviderTestSuite;
import org.jboss.as.test.integration.management.interfaces.JmxManagementInterface;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.as.test.integration.management.rbac.UserRolesMappingServerSetupTask;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.test.jmx.JMXServiceDeploymentSetupTask;

/**
 * Tests of server group scoped roles using the "rbac" access control provider.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class JmxRBACProviderServerGroupScopedRolesTestCase extends AbstractServerGroupScopedRolesTestCase {
    private static JMXServiceDeploymentSetupTask jmxTask = new JMXServiceDeploymentSetupTask();
    public static final String  OBJECT_NAME = "jboss.test:service=testdeployments";
    private static final String OTHER_GROUP_USER = "OtherGroupSuperUser";
    private static final String OTHER_GROUP_ROLE = "OtherGroupSuperUser";
    private boolean mbeanSensitivity = true;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = FullRbacProviderTestSuite.createSupport(JmxRBACProviderServerGroupScopedRolesTestCase.class.getSimpleName());
        primaryClientConfig = testSupport.getDomainPrimaryConfiguration();
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        setupRoles(domainClient);
        setNonCoreMbeanSensitivity(domainClient, true);
        ServerGroupRolesMappingSetup.INSTANCE.setup(domainClient);
        deployDeployment1(domainClient);
        jmxTask.setup(domainClient, SERVER_GROUP_A);
    }

    protected static void setupRoles(DomainClient domainClient) throws IOException {
        AbstractServerGroupScopedRolesTestCase.setupRoles(domainClient);
        ModelNode op = createOpNode(SCOPED_ROLE + OTHER_GROUP_ROLE, ADD);
        op.get(BASE_ROLE).set(RbacUtil.SUPERUSER_USER);
        op.get(SERVER_GROUPS).add(SERVER_GROUP_B);
        RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
    }

    protected static void tearDownRoles(DomainClient domainClient) throws IOException {
        AbstractServerGroupScopedRolesTestCase.tearDownRoles(domainClient);
        ModelNode op = createOpNode(SCOPED_ROLE + OTHER_GROUP_ROLE, REMOVE);
        RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
    }

    private static void setNonCoreMbeanSensitivity(DomainClient domainClient, boolean sensitivity) throws IOException {
        ModelNode op = Operations.createWriteAttributeOperation(
                PathAddress.pathAddress(PathElement.pathElement(PROFILE, "profile-a"), PathElement.pathElement(SUBSYSTEM, "jmx")).toModelNode(),
                "non-core-mbean-sensitivity", sensitivity);
        ModelNode result = domainClient.execute(op);
        assertTrue(Operations.isSuccessfulOutcome(result));
        op = Operations.createWriteAttributeOperation(
                PathAddress.pathAddress(PathElement.pathElement(PROFILE, "profile-b"), PathElement.pathElement(SUBSYSTEM, "jmx")).toModelNode(),
                "non-core-mbean-sensitivity", sensitivity);
        result = domainClient.execute(op);
        assertTrue(Operations.isSuccessfulOutcome(result));
    }

    @After
    public void activateMBeanSensitivity() throws IOException {
        mbeanSensitivity = true;
        setNonCoreMbeanSensitivity(testSupport.getDomainPrimaryLifecycleUtil().getDomainClient(), true);
    }

    protected void deactivateMBeanSensitivity() throws IOException {
        mbeanSensitivity = false;
        setNonCoreMbeanSensitivity(testSupport.getDomainPrimaryLifecycleUtil().getDomainClient(), false);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient domainClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        try {
            ServerGroupRolesMappingSetup.INSTANCE.tearDown(domainClient);
        } finally {
            try {
                tearDownRoles(domainClient);
            } finally {
                try {
                    removeDeployment1(domainClient);
                } finally {
                    try {
                        setNonCoreMbeanSensitivity(domainClient, false);
                        jmxTask.tearDown(domainClient, SERVER_GROUP_A);
                    } finally {
                        FullRbacProviderTestSuite.stopSupport();
                        testSupport = null;
                    }
                }
            }
        }
    }

    @Test
    @Override
    public void testMonitor() throws Exception {
        test(MONITOR_USER);
    }

    @Test
    @Override
    public void testOperator() throws Exception {
        test(OPERATOR_USER);
    }

    @Test
    @Override
    public void testMaintainer() throws Exception {
        test(MAINTAINER_USER);
    }

    @Test
    @Override
    public void testDeployer() throws Exception {
        test(DEPLOYER_USER);
    }

    @Test
    @Override
    public void testAdministrator() throws Exception {
        test(ADMINISTRATOR_USER);
    }

    @Test
    @Override
    public void testAuditor() throws Exception {
        test(AUDITOR_USER);
    }

    @Test
    @Override
    public void testSuperUser() throws Exception {
        test(SUPERUSER_USER);
    }

    @Test
    public void testOtherSuperUser() throws Exception {
        test(OTHER_GROUP_USER);
    }

    protected boolean isReadAllowed(String userName) {
        switch(userName) {
            case MONITOR_USER:
            case DEPLOYER_USER:
            case MAINTAINER_USER:
            case OPERATOR_USER:
                return !mbeanSensitivity;
            case ADMINISTRATOR_USER:
            case AUDITOR_USER:
            case SUPERUSER_USER:
                return true;
            default:
                return false;
        }
    }

    protected boolean isWriteAllowed(String userName) {
        switch(userName) {
            case MAINTAINER_USER:
            case OPERATOR_USER:
                return !mbeanSensitivity;
            case ADMINISTRATOR_USER:
            case SUPERUSER_USER:
                return true;
            case MONITOR_USER:
            case AUDITOR_USER:
            case DEPLOYER_USER:
            default:
                return false;
        }
    }

    private void test(String userName) throws Exception {
        String urlString = System.getProperty("jmx.service.url", "service:jmx:remoting-jmx://"
                        + NetworkUtils.formatPossibleIpv6Address(primaryClientConfig.getHostControllerManagementAddress()) + ":12345");
        JmxManagementInterface jmx = JmxManagementInterface.create(new JMXServiceURL(urlString),
                userName, RbacAdminCallbackHandler.STD_PASSWORD,
                null // not needed, as the only thing from JmxManagementInterface used in this test is getConnection()
        );
        try {
            getAttribute(userName, jmx);
            setAttribute(userName, jmx);
            dumpServices(userName, jmx);
            operationReadOnly(userName, jmx);
            operationWriteOnly(userName, jmx);
            operationReadWrite(userName, jmx);
            operationUnknown(userName, jmx);
            deactivateMBeanSensitivity();
            getAttribute(userName, jmx);
            setAttribute(userName, jmx);
            dumpServices(userName, jmx);
            operationReadOnly(userName, jmx);
            operationWriteOnly(userName, jmx);
            operationReadWrite(userName, jmx);
            operationUnknown(userName, jmx);
        } finally {
            jmx.close();
        }
    }

    // test utils

    private void getAttribute(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isReadAllowed(userName);
        MBeanServerConnection connection = jmx.getConnection();
        ObjectName domain = new ObjectName("java.lang:type=OperatingSystem");
        try {
            Object attribute = connection.getAttribute(domain, "Name");
            assertTrue("Failure was expected", successExpected);
            assertEquals(System.getProperty("os.name"), attribute.toString());
        } catch (JMRuntimeException e) {
            if (e.getMessage().contains("WFLYJMX0037")) {
                assertFalse("Success was expected but failure happened: " + e, successExpected);
            } else {
                throw e;
            }
        }
    }

    private void setAttribute(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isWriteAllowed(userName);

        MBeanServerConnection connection = jmx.getConnection();
        ObjectName domain = new ObjectName("java.lang:type=Memory");
        try {
            connection.setAttribute(domain, new Attribute("Verbose", true));
            connection.setAttribute(domain, new Attribute("Verbose", false)); // back to default to not pollute the logs
            assertTrue("Failure was expected", successExpected);
        } catch (JMRuntimeException e) {
            if (e.getMessage().contains("WFLYJMX0037")) {
                assertFalse("Success was expected but failure happened: " + e, successExpected);
            } else {
                throw e;
            }
        }
    }

    private void dumpServices(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isWriteAllowed(userName);
        doOperation(successExpected, "jboss.msc:type=container,name=jboss-as", "dumpServices", jmx);
    }

    private void operationReadOnly(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isReadAllowed(userName);
        doOperation(successExpected, OBJECT_NAME, "helloReadOnly", jmx);
    }

    private void operationWriteOnly(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isWriteAllowed(userName);
        doOperation(successExpected, OBJECT_NAME, "helloWriteOnly", jmx);
    }

    private void operationReadWrite(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isWriteAllowed(userName);
        doOperation(successExpected, OBJECT_NAME, "helloReadWrite", jmx);
    }

    private void operationUnknown(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isWriteAllowed(userName);
        doOperation(successExpected, OBJECT_NAME, "helloUnknown", jmx);
    }

    private void doOperation(boolean successExpected, String objectName, String operationName, JmxManagementInterface jmx) throws Exception {
        MBeanServerConnection connection = jmx.getConnection();
        ObjectName domain = new ObjectName(objectName);
        try {
            connection.invoke(domain, operationName, ArrayUtils.EMPTY_OBJECT_ARRAY, ArrayUtils.EMPTY_STRING_ARRAY);
            assertTrue("Failure was expected but success happened", successExpected);
        } catch (JMRuntimeException e) {
            if (e.getMessage().contains("WFLYJMX0037")) {
                assertFalse("Success was expected but failure happened: " + e, successExpected);
            } else {
                throw e;
            }
        }
    }

    @Override
    protected boolean isAllowLocalAuth() {
        return false;
    }

    @Override
    protected void configureRoles(ModelNode op, String[] roles) {
        // no-op. Role mapping is done based on the client's authenticated Subject
    }

    static class ServerGroupRolesMappingSetup extends UserRolesMappingServerSetupTask {

        private static final Map<String, Set<String>> STANDARD_USERS;

        static {
            Map<String, Set<String>> rolesToUsers = new HashMap<String, Set<String>>();
            rolesToUsers.put(MONITOR_USER, Collections.singleton(MONITOR_USER));
            rolesToUsers.put(OPERATOR_USER, Collections.singleton(OPERATOR_USER));
            rolesToUsers.put(MAINTAINER_USER, Collections.singleton(MAINTAINER_USER));
            rolesToUsers.put(DEPLOYER_USER, Collections.singleton(DEPLOYER_USER));
            rolesToUsers.put(ADMINISTRATOR_USER, Collections.singleton(ADMINISTRATOR_USER));
            rolesToUsers.put(AUDITOR_USER, Collections.singleton(AUDITOR_USER));
            rolesToUsers.put(SUPERUSER_USER, Collections.singleton(SUPERUSER_USER));
            rolesToUsers.put(OTHER_GROUP_ROLE, Collections.singleton(OTHER_GROUP_USER));
            STANDARD_USERS = rolesToUsers;
        }

        static final ServerGroupRolesMappingSetup INSTANCE = new ServerGroupRolesMappingSetup();

        protected ServerGroupRolesMappingSetup() {
            super(STANDARD_USERS);
        }
    }
}
