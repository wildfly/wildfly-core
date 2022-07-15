/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.REMOTE_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOWED_ORIGINS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_DATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECYCLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.ADMINISTRATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.AUDITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.DEPLOYER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.OPERATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_USER;
import static org.jboss.as.test.integration.mgmt.access.InMemoryAuditReportSetupTask.IN_MEMORY_HANDLER_ADDR;
import static org.productivity.java.syslog4j.impl.message.pci.PCISyslogMessage.USER_ID;

import java.util.List;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.audit.InMemoryAuditLogHandlerResourceDefinition;
import org.jboss.as.test.integration.management.interfaces.CliManagementInterface;
import org.jboss.as.test.integration.management.interfaces.ManagementInterface;
import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.hamcrest.MatcherAssert.assertThat;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;


/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({InMemoryAuditReportSetupTask.class, StandardUsersSetupTask.class})
public class InMemoryAuditReportTestCase extends AbstractManagementInterfaceRbacTestCase {

    private static final PathAddress ALLOWED_ORIGINS_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(MANAGEMENT_INTERFACE, HTTP_INTERFACE);
    private static final PathAddress SYSTEM_PROPERTY_ADDRESS = PathAddress.pathAddress()
            .append(SYSTEM_PROPERTY, "test");

    @Before
    public void createConfigurationChanges() throws Exception {
        ManagementInterface client = getClientForUser(SUPERUSER_USER);
        ModelNode setAllowedOrigins = Util.getWriteAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ALLOWED_ORIGINS, "http://www.wildfly.org");
        client.execute(setAllowedOrigins);
        ModelNode setSystemProperty = Util.createAddOperation(SYSTEM_PROPERTY_ADDRESS);
        setSystemProperty.get(VALUE).set("changeConfig");
        client.execute(setSystemProperty);
        ModelNode readSystemProperty = Util.createOperation(READ_RESOURCE_OPERATION, SYSTEM_PROPERTY_ADDRESS);
        client.execute(readSystemProperty);
        ModelNode unsetAllowedOrigins = Util.getUndefineAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ALLOWED_ORIGINS);
        client.execute(unsetAllowedOrigins);
        ModelNode unsetSystemProperty = Util.createRemoveOperation(SYSTEM_PROPERTY_ADDRESS);
        client.execute(unsetSystemProperty);
    }

    @After
    public void clearConfigurationChanges() throws UnsuccessfulOperationException {
        ModelNode recycle = Util.createEmptyOperation(RECYCLE, IN_MEMORY_HANDLER_ADDR);
        getManagementClient().executeForResult(recycle);
    }

    @Override
    protected ManagementInterface createClient(String userName) {
        return CliManagementInterface.create(
                getManagementClient().getMgmtAddress(), getManagementClient().getMgmtPort(),
                userName, RbacAdminCallbackHandler.STD_PASSWORD
        );
    }

    @Test
    public void testMonitor() throws Exception {
        ManagementInterface client = getClientForUser(MONITOR_USER);
        readConfigurationChanges(client, false);
    }

    @Test
    public void testOperator() throws Exception {
        ManagementInterface client = getClientForUser(OPERATOR_USER);
        readConfigurationChanges(client, false);
    }

    @Test
    public void testMaintainer() throws Exception {
        ManagementInterface client = getClientForUser(MAINTAINER_USER);
        readConfigurationChanges(client, false);
    }

    @Test
    public void testDeployer() throws Exception {
        ManagementInterface client = getClientForUser(DEPLOYER_USER);
        readConfigurationChanges(client, false);
    }

    @Test
    public void testAdministrator() throws Exception {
        ManagementInterface client = getClientForUser(ADMINISTRATOR_USER);
        readConfigurationChanges(client, false);
    }

    @Test
    public void testAuditor() throws Exception {
        ManagementInterface client = getClientForUser(AUDITOR_USER);
        readConfigurationChanges(client, true);
    }

    @Test
    public void testSuperUser() throws Exception {
        ManagementInterface client = getClientForUser(SUPERUSER_USER);
        readConfigurationChanges(client, true);
    }

    @Test
    public void testChangeMaxHistory() throws Exception {
        try {
            ModelNode reduceMaxHistory = Util.getWriteAttributeOperation(IN_MEMORY_HANDLER_ADDR, ModelDescriptionConstants.MAX_HISTORY, 2);
            getManagementClient().executeForResult(reduceMaxHistory);
            ManagementInterface client = getClientForUser(SUPERUSER_USER);
            ModelNode readConfigChanges = Util.createEmptyOperation(InMemoryAuditLogHandlerResourceDefinition.OPERATION_NAME, IN_MEMORY_HANDLER_ADDR);
            ModelNode response = client.execute(readConfigChanges);
            assertThat(response.asString(), response.get(OUTCOME).asString(), is(SUCCESS));
            List<ModelNode> changes = response.get(RESULT).asList();
            assertThat(changes.size(), is(2));
            for (ModelNode change : changes) {
                assertThat(change.hasDefined(OPERATION_DATE), is(true));
                assertThat(change.hasDefined(USER_ID), is(false));
                assertThat(change.hasDefined(DOMAIN_UUID), is(false));
                assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
                assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
                assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
                assertThat(change.toJSONString(true), change.get(OUTCOME).asString(), is(SUCCESS));
                assertThat(change.get(OPERATIONS).asList().size(), is(1));
            }
            ModelNode currentChange = changes.get(0);
            ModelNode currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
            assertThat(currentChangeOp.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
            assertThat(currentChangeOp.get(OP_ADDR).asString(), is(IN_MEMORY_HANDLER_ADDR.toModelNode().asString()));
            currentChange = changes.get(1);
            currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
            assertThat(currentChangeOp.get(OP).asString(), is(REMOVE));
            assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toString()));


        }finally {
            ModelNode reallowMaxHistory = Util.getWriteAttributeOperation(IN_MEMORY_HANDLER_ADDR, ModelDescriptionConstants.MAX_HISTORY, 3);
            getManagementClient().executeForResult(reallowMaxHistory);
        }
    }

    private void readConfigurationChanges(ManagementInterface client, boolean authorized) {
        ModelNode readConfigChanges = Util.createEmptyOperation(InMemoryAuditLogHandlerResourceDefinition.OPERATION_NAME, IN_MEMORY_HANDLER_ADDR);
        ModelNode response = client.execute(readConfigChanges);
        if (authorized) {
            assertThat(response.asString(), response.get(OUTCOME).asString(), is(SUCCESS));
            List<ModelNode> changes = response.get(RESULT).asList();
            assertThat(changes.size(), is(3));
            for (ModelNode change : changes) {
                assertThat(change.hasDefined(OPERATION_DATE), is(true));
                assertThat(change.hasDefined(USER_ID), is(false));
                assertThat(change.hasDefined(DOMAIN_UUID), is(false));
                assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
                assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
                assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
                assertThat(change.toJSONString(true), change.get(OUTCOME).asString(), is(SUCCESS));
                assertThat(change.get(OPERATIONS).asList().size(), is(1));
            }
            ModelNode currentChange = changes.get(0);
            ModelNode currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
            assertThat(currentChangeOp.get(OP).asString(), is(REMOVE));
            assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toString()));
            currentChange = changes.get(1);
            currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
            assertThat(currentChangeOp.get(OP).asString(), is(UNDEFINE_ATTRIBUTE_OPERATION));
            assertThat(currentChangeOp.get(OP_ADDR).asString(), is(ALLOWED_ORIGINS_ADDRESS.toModelNode().asString()));
            currentChange = changes.get(2);
            currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
            assertThat(currentChangeOp.get(OP).asString(), is(ADD));
            assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toModelNode().asString()));
        } else {
            assertThat(response.get(OUTCOME).asString(), is(FAILED));
        }

    }
}
