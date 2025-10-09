/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.REMOTE_ADDRESS;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.USER_ID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOWED_ORIGINS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT_LOG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURED_REQUIRES_ADDRESSABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURED_REQUIRES_READ;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONSTRAINT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_MEMORY_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOGGER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOG_BOOT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_HISTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_DATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SENSITIVITY_CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.ADMINISTRATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.AUDITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.DEPLOYER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.OPERATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_USER;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.interfaces.CliManagementInterface;
import org.jboss.as.test.integration.management.interfaces.ManagementInterface;
import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({ServerReload.SetupTask.class, StandardUsersSetupTask.class})
public class ConfigurationChangesHistoryTestCase extends AbstractManagementInterfaceRbacTestCase {

    private static final int MAX_HISTORY_SIZE = 8;

    private static final PathAddress ALLOWED_ORIGINS_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(MANAGEMENT_INTERFACE, HTTP_INTERFACE);
    private static final PathAddress AUDIT_LOG_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(ACCESS, AUDIT)
            .append(LOGGER, AUDIT_LOG);
    private static final PathAddress SYSTEM_PROPERTY_CLASSIFICATION_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(ACCESS, AUTHORIZATION)
            .append(CONSTRAINT, SENSITIVITY_CLASSIFICATION)
            .append(TYPE, CORE)
            .append(CLASSIFICATION, SYSTEM_PROPERTY);
    private static final PathAddress SYSTEM_PROPERTY_ADDRESS = PathAddress.pathAddress()
            .append(SYSTEM_PROPERTY, "test");
    private static final PathAddress ADDRESS = PathAddress.pathAddress()
            .append(PathElement.pathElement(SUBSYSTEM, "core-management"))
            .append(PathElement.pathElement("service", "configuration-changes"));
    private static final PathAddress IN_MEMORY_HANDLER_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(ACCESS, AUDIT)
            .append(IN_MEMORY_HANDLER, "test");

    @Before
    public void createConfigurationChanges() throws Exception {
        ManagementInterface client = getClientForUser(SUPERUSER_USER);
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(ADDRESS));
        add.get("max-history").set(MAX_HISTORY_SIZE);
        client.execute(add);
        // WFCORE-3995 sensitivity classification system property default configured-requires-read is false.
        // Need to write configured-requires-read before configured-requires-addressable
        ModelNode configureSensitivity = Util.getWriteAttributeOperation(SYSTEM_PROPERTY_CLASSIFICATION_ADDRESS, CONFIGURED_REQUIRES_READ, true);
        client.execute(configureSensitivity);
        configureSensitivity = Util.getWriteAttributeOperation(SYSTEM_PROPERTY_CLASSIFICATION_ADDRESS, CONFIGURED_REQUIRES_ADDRESSABLE, true);
        client.execute(configureSensitivity);
        ModelNode setAllowedOrigins = Util.createEmptyOperation("list-add", ALLOWED_ORIGINS_ADDRESS);
        setAllowedOrigins.get(NAME).set(ALLOWED_ORIGINS);
        setAllowedOrigins.get(VALUE).set("https://www.wildfly.org");
        client.execute(setAllowedOrigins);
        ModelNode disableLogBoot = Util.getWriteAttributeOperation(AUDIT_LOG_ADDRESS, LOG_BOOT, false);
        client.execute(disableLogBoot);
        //read
        client.execute(Util.getReadAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ALLOWED_ORIGINS));
        //invalid operation
        client.execute(Util.getUndefineAttributeOperation(ALLOWED_ORIGINS_ADDRESS, "not-exists-attribute"));
        //invalid operation
        client.execute(Util.getWriteAttributeOperation(ALLOWED_ORIGINS_ADDRESS, "not-exists-attribute", "123456"));
        //write operation, failed
        ModelNode setAllowedOriginsFails = Util.getWriteAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ALLOWED_ORIGINS, "123456");//wrong type, expected is LIST, op list-add
        client.execute(setAllowedOriginsFails);
        ModelNode setSystemProperty = Util.createAddOperation(SYSTEM_PROPERTY_ADDRESS);
        setSystemProperty.get(VALUE).set("changeConfig");
        client.execute(setSystemProperty);
        ModelNode unsetAllowedOrigins = Util.getUndefineAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ALLOWED_ORIGINS);
        client.execute(unsetAllowedOrigins);
        ModelNode enableLogBoot = Util.getWriteAttributeOperation(AUDIT_LOG_ADDRESS, LOG_BOOT, true);
        client.execute(enableLogBoot);
        ModelNode unsetSystemProperty = Util.createRemoveOperation(SYSTEM_PROPERTY_ADDRESS);
        client.execute(unsetSystemProperty);
        ModelNode addInMemoryHandler = Util.createAddOperation(IN_MEMORY_HANDLER_ADDRESS);
        client.execute(addInMemoryHandler);
        ModelNode editInMemoryHandler = Util.getWriteAttributeOperation(IN_MEMORY_HANDLER_ADDRESS, MAX_HISTORY, 50);
        client.execute(editInMemoryHandler);
        ModelNode removeInMemoryHandler = Util.createRemoveOperation(IN_MEMORY_HANDLER_ADDRESS);
        client.execute(removeInMemoryHandler);
    }

    @After
    public void clearConfigurationChanges() throws UnsuccessfulOperationException {
        final ModelNode remove = Util.createRemoveOperation(ADDRESS);
        getManagementClient().executeForResult(remove);
        ModelNode configureSensitivity = Util.getUndefineAttributeOperation(SYSTEM_PROPERTY_CLASSIFICATION_ADDRESS, CONFIGURED_REQUIRES_ADDRESSABLE);
        getManagementClient().executeForResult(configureSensitivity);
        configureSensitivity = Util.getUndefineAttributeOperation(SYSTEM_PROPERTY_CLASSIFICATION_ADDRESS, CONFIGURED_REQUIRES_READ);
        getManagementClient().executeForResult(configureSensitivity);
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
        readConfigurationChanges(client, false, false);
    }

    @Test
    public void testOperator() throws Exception {
        ManagementInterface client = getClientForUser(OPERATOR_USER);
        readConfigurationChanges(client, false, false);
    }

    @Test
    public void testMaintainer() throws Exception {
        ManagementInterface client = getClientForUser(MAINTAINER_USER);
        readConfigurationChanges(client, false, false);
    }

    @Test
    public void testDeployer() throws Exception {
        ManagementInterface client = getClientForUser(DEPLOYER_USER);
        readConfigurationChanges(client, false, false);
    }

    @Test
    public void testAdministrator() throws Exception {
        ManagementInterface client = getClientForUser(ADMINISTRATOR_USER);
        readConfigurationChanges(client, true, false);
    }

    @Test
    public void testAuditor() throws Exception {
        ManagementInterface client = getClientForUser(AUDITOR_USER);
        readConfigurationChanges(client, false, true);
    }

    @Test
    public void testSuperUser() throws Exception {
        ManagementInterface client = getClientForUser(SUPERUSER_USER);
        readConfigurationChanges(client, true, true);
    }

    private void readConfigurationChanges(ManagementInterface client, boolean authorized, boolean auditAuthorized) {
        ModelNode readConfigChanges = Util.createOperation("list-changes", ADDRESS);
        ModelNode response = client.execute(readConfigChanges);
        assertThat(response.asString(), response.get(OUTCOME).asString(), is(SUCCESS));
        List<ModelNode> changes = response.get(RESULT).asList();
        assertThat(changes.size(), is(MAX_HISTORY_SIZE));
        for (ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(false));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(change.get(OPERATIONS).asList().size(), is(1));
        }
        ModelNode currentChange = changes.get(0);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        ModelNode currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(REMOVE));
        if (auditAuthorized) {
            assertThat(PathAddress.pathAddress(currentChangeOp.get(OP_ADDR)).toString(), is(IN_MEMORY_HANDLER_ADDRESS.toString()));
        } else {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), containsString("WFLYCTL0332"));
        }
        currentChange = changes.get(1);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        if (auditAuthorized) {
            assertThat(PathAddress.pathAddress(currentChangeOp.get(OP_ADDR)).toString(), is(IN_MEMORY_HANDLER_ADDRESS.toString()));
            assertThat(currentChangeOp.get(VALUE).asInt(), is(50));
        } else {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), containsString("WFLYCTL0332"));
        }
        currentChange = changes.get(2);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(ADD));
        if (auditAuthorized) {
            assertThat(PathAddress.pathAddress(currentChangeOp.get(OP_ADDR)).toString(), is(IN_MEMORY_HANDLER_ADDRESS.toString()));
        } else {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), containsString("WFLYCTL0332"));
        }
        currentChange = changes.get(3);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(REMOVE));
        if (authorized || auditAuthorized) {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toString()));
        } else {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), containsString("WFLYCTL0332"));
        }
        currentChange = changes.get(4);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        if (auditAuthorized) {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), is(AUDIT_LOG_ADDRESS.toModelNode().asString()));
            assertThat(currentChangeOp.get(VALUE).asBoolean(), is(true));
        } else {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), containsString("WFLYCTL0332"));
        }
        currentChange = changes.get(5);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(UNDEFINE_ATTRIBUTE_OPERATION));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(ALLOWED_ORIGINS_ADDRESS.toModelNode().asString()));
        if (authorized) {
            assertThat(currentChangeOp.get(NAME).asString(), is(ALLOWED_ORIGINS));
        } else {
            assertThat(currentChangeOp.get(NAME).isDefined(), is(false));
        }
        currentChange = changes.get(6);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(ADD));
        if (authorized || auditAuthorized) {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toModelNode().asString()));
            if (authorized) {
                assertThat(currentChangeOp.get(VALUE).asString(), is("changeConfig"));
            } else {
                assertThat(currentChangeOp.hasDefined(NAME), is(false));
                assertThat(currentChangeOp.hasDefined(VALUE), is(false));
            }
        } else {
            assertThat(currentChangeOp.get(OP_ADDR).asString(), containsString("WFLYCTL0332"));
            assertThat(currentChangeOp.asString(), currentChangeOp.hasDefined(NAME), is(false));
        }
        currentChange = changes.get(7);
        assertThat(currentChange.get(OUTCOME).asString(), is(FAILED));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        if (authorized) {
            assertThat(currentChangeOp.get(NAME).asString(), is(ALLOWED_ORIGINS));
        } else {
            assertThat(currentChangeOp.get(NAME).isDefined(), is(false));
        }
    }
}
