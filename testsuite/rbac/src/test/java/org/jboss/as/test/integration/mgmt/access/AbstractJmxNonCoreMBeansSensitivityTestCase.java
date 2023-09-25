/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jakarta.inject.Inject;
import javax.management.Attribute;
import javax.management.JMRuntimeException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.commons.lang3.ArrayUtils;
import org.jboss.as.test.integration.management.interfaces.JmxManagementInterface;
import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public abstract class AbstractJmxNonCoreMBeansSensitivityTestCase {
    @Inject
    private ManagementClient managementClient;

    protected abstract boolean isReadAllowed(String userName);

    protected abstract boolean isWriteAllowed(String userName);

    @Test
    public void testMonitor() throws Exception {
        test(RbacUtil.MONITOR_USER);
    }

    @Test
    public void testOperator() throws Exception {
        test(RbacUtil.OPERATOR_USER);
    }

    @Test
    public void testMaintainer() throws Exception {
        test(RbacUtil.MAINTAINER_USER);
    }

    @Test
    public void testDeployer() throws Exception {
        test(RbacUtil.DEPLOYER_USER);
    }

    @Test
    public void testAdministrator() throws Exception {
        test(RbacUtil.ADMINISTRATOR_USER);
    }

    @Test
    public void testAuditor() throws Exception {
        test(RbacUtil.AUDITOR_USER);
    }

    @Test
    public void testSuperUser() throws Exception {
        test(RbacUtil.SUPERUSER_USER);
    }

    private void test(String userName) throws Exception {
        JmxManagementInterface jmx = JmxManagementInterface.create(
                managementClient.getRemoteJMXURL(),
                userName, RbacAdminCallbackHandler.STD_PASSWORD,
                null // not needed, as the only thing from JmxManagementInterface used in this test is getConnection()
        );
        try {
            getAttribute(userName, jmx);
            setAttribute(userName, jmx);

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

    private void operationReadOnly(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isReadAllowed(userName);
        doOperation(successExpected, "helloReadOnly", jmx);
    }

    private void operationWriteOnly(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isWriteAllowed(userName);
        doOperation(successExpected, "helloWriteOnly", jmx);
    }

    private void operationReadWrite(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isWriteAllowed(userName);
        doOperation(successExpected, "helloReadWrite", jmx);
    }

    private void operationUnknown(String userName, JmxManagementInterface jmx) throws Exception {
        boolean successExpected = isWriteAllowed(userName);
        doOperation(successExpected, "helloUnknown", jmx);
    }

    private void doOperation(boolean successExpected, String operationName, JmxManagementInterface jmx) throws Exception {
        MBeanServerConnection connection = jmx.getConnection();
        ObjectName domain = new ObjectName("jboss.test:service=testdeployments");
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
}
