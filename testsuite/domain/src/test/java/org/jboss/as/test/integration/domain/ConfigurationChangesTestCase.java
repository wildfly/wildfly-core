/*
 * Copyright (C) 2016 Red Hat, inc., and individual contributors
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
package org.jboss.as.test.integration.domain;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.REMOTE_ADDRESS;
import static org.jboss.as.controller.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOWED_ORIGINS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT_LOG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertThat;
import static org.productivity.java.syslog4j.impl.message.pci.PCISyslogMessage.USER_ID;

import java.io.IOException;
import java.util.List;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.ConfigurationChangeResourceDefinition;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ConfigurationChangesTestCase {
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;
    private static final Logger logger = Logger.getLogger(ConfigurationChangesTestCase.class);

    private static final int MAX_HISTORY_SIZE = 100;

    private static final PathElement HOST_MASTER = PathElement.pathElement(HOST, "master");
    private static final PathElement HOST_SLAVE = PathElement.pathElement(HOST, "slave");
    private static final PathAddress ALLOWED_ORIGINS_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(MANAGEMENT_INTERFACE, HTTP_INTERFACE);
    private static final PathAddress AUDIT_LOG_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(ACCESS, AUDIT)
            .append(LOGGER, AUDIT_LOG);
    private static final PathAddress SYSTEM_PROPERTY_ADDRESS = PathAddress.pathAddress()
            .append(SYSTEM_PROPERTY, "test");
    private static final PathAddress ADDRESS = PathAddress.pathAddress()
            .append(PathElement.pathElement(CORE_SERVICE, MANAGEMENT))
            .append(ConfigurationChangeResourceDefinition.PATH);
    private static final PathAddress IN_MEMORY_HANDLER_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(ACCESS, AUDIT)
            .append(IN_MEMORY_HANDLER, "test");

    @BeforeClass
    public static void setupDomain() throws Exception {
         DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(ConfigurationChangesTestCase.class.getSimpleName(),
                    "domain-configs/domain-config-changes.xml", "host-configs/host-master-config-changes.xml", "host-configs/host-slave-config-changes.xml",
                    false, false, false, false, false);
        testSupport = DomainTestSupport.createAndStartSupport(configuration);
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.stop();
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
        testSupport = null;
    }

    public void createConfigurationChanges(PathElement host) throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress().append(host).append(ADDRESS));
        add.get(ConfigurationChangeResourceDefinition.MAX_HISTORY.getName()).set(MAX_HISTORY_SIZE);
        client.execute(add);
        PathAddress allowedOrigins = PathAddress.pathAddress().append(host).append(ALLOWED_ORIGINS_ADDRESS);
        ModelNode setAllowedOrigins = Util.createEmptyOperation("list-add", allowedOrigins);
        setAllowedOrigins.get(NAME).set(ALLOWED_ORIGINS);
        setAllowedOrigins.get(VALUE).set( "http://www.wildfly.org");
        client.execute(setAllowedOrigins);
        PathAddress auditLogAddress = PathAddress.pathAddress().append(host).append(AUDIT_LOG_ADDRESS);
        ModelNode disableLogBoot = Util.getWriteAttributeOperation(auditLogAddress, LOG_BOOT, false);
        client.execute(disableLogBoot);
        //read
        client.execute(Util.getReadAttributeOperation(allowedOrigins, ALLOWED_ORIGINS));
        //invalid operation
        client.execute(Util.getUndefineAttributeOperation(allowedOrigins, "not-exists-attribute"));
        //invalid operation
        client.execute(Util.getWriteAttributeOperation(allowedOrigins, "not-exists-attribute", "123456"));
        //write operation, failed
        ModelNode setAllowedOriginsFails = Util.getWriteAttributeOperation(allowedOrigins, ALLOWED_ORIGINS, "123456");//wrong type, expected is LIST, op list-add
        client.execute(setAllowedOriginsFails);
        PathAddress systemPropertyAddress = PathAddress.pathAddress().append(host).append(SYSTEM_PROPERTY_ADDRESS);
        ModelNode setSystemProperty = Util.createAddOperation(systemPropertyAddress);
        setSystemProperty.get(VALUE).set("changeConfig");
        client.execute(setSystemProperty);
        ModelNode unsetAllowedOrigins = Util.getUndefineAttributeOperation(allowedOrigins, ALLOWED_ORIGINS);
        client.execute(unsetAllowedOrigins);
        ModelNode enableLogBoot = Util.getWriteAttributeOperation(auditLogAddress, LOG_BOOT, true);
        client.execute(enableLogBoot);
        ModelNode unsetSystemProperty = Util.createRemoveOperation(systemPropertyAddress);
        client.execute(unsetSystemProperty);
        PathAddress inMemoryAddress = PathAddress.pathAddress().append(host).append(IN_MEMORY_HANDLER_ADDRESS);
        ModelNode addInMemoryHandler = Util.createAddOperation(inMemoryAddress);
        client.execute(addInMemoryHandler);
        ModelNode editInMemoryHandler = Util.getWriteAttributeOperation(inMemoryAddress, MAX_HISTORY, 50);
        client.execute(editInMemoryHandler);
        ModelNode removeInMemoryHandler = Util.createRemoveOperation(inMemoryAddress);
        client.execute(removeInMemoryHandler);
    }

    @Test
    public void testConfigurationChanges() throws Exception {
        createConfigurationChanges(HOST_MASTER);
        createConfigurationChanges(HOST_SLAVE);
        try {
            checkConfigurationChanges(readConfigurationChanges(domainMasterLifecycleUtil.getDomainClient(), HOST_MASTER), 11);
            checkConfigurationChanges(readConfigurationChanges(domainMasterLifecycleUtil.getDomainClient(), HOST_SLAVE), 11);
            checkConfigurationChanges(readConfigurationChanges(domainSlaveLifecycleUtil.getDomainClient(), HOST_SLAVE), 11);
            checkRootConfigurationChangeWarning(domainMasterLifecycleUtil.getDomainClient());
            setConfigurationChangeMaxHistory(domainMasterLifecycleUtil.getDomainClient(), HOST_MASTER, 20);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 20, HOST_MASTER, PathElement.pathElement(SERVER, "main-one"));
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
        } finally {
            clearConfigurationChanges(HOST_MASTER);
            clearConfigurationChanges(HOST_SLAVE);
        }
    }

    @Test
    public void testConfigurationChangesOnSlave() throws Exception {
        createConfigurationChanges(HOST_SLAVE);
        try {
            PathAddress systemPropertyAddress = PathAddress.pathAddress().append(HOST_SLAVE).append(SYSTEM_PROPERTY, "slave");
            ModelNode setSystemProperty = Util.createAddOperation(systemPropertyAddress);
            setSystemProperty.get(VALUE).set("slave-config");
            domainSlaveLifecycleUtil.getDomainClient().execute(setSystemProperty);
            systemPropertyAddress = PathAddress.pathAddress().append(SERVER_GROUP, "main-server-group").append(SYSTEM_PROPERTY, "main");
            setSystemProperty = Util.createAddOperation(systemPropertyAddress);
            setSystemProperty.get(VALUE).set("main-config");
            domainMasterLifecycleUtil.getDomainClient().execute(setSystemProperty);
            List<ModelNode> changesOnSlaveHC = readConfigurationChanges(domainSlaveLifecycleUtil.getDomainClient(), HOST_SLAVE);
            List<ModelNode> changesOnSlaveDC = readConfigurationChanges(domainMasterLifecycleUtil.getDomainClient(), HOST_SLAVE);
            checkSlaveConfigurationChanges(changesOnSlaveHC, 12);
            setConfigurationChangeMaxHistory(domainMasterLifecycleUtil.getDomainClient(), HOST_SLAVE, 20);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 20, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSlaveLifecycleUtil.getDomainClient(), 20, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            assertThat(changesOnSlaveHC.size(), is(changesOnSlaveDC.size()));
        } finally {
            clearConfigurationChanges(HOST_SLAVE);
        }
    }

    private void checkRootConfigurationChangeWarning(DomainClient client) throws IOException {
        PathAddress address = ADDRESS;
        ModelNode addConfigurationChanges = Util.createAddOperation(address);
        addConfigurationChanges.get(ConfigurationChangeResourceDefinition.MAX_HISTORY.getName()).set(MAX_HISTORY_SIZE);
        ModelNode response = client.execute(addConfigurationChanges);
        assertThat(response.asString(), response.get(OUTCOME).asString(), is(SUCCESS));
        assertThat(response.get(RESULT).asString(), containsString("WFLYDM0135"));
    }

    private void checkConfigurationChanges(List<ModelNode> changes, int size) throws IOException {
        assertThat(changes.size(), is(size));
        for (ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(true));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(change.get(OPERATIONS).asList().size(), is(1));
        }
        validateChanges(changes);
    }

    private void checkSlaveConfigurationChanges( List<ModelNode> changes, int size) throws IOException {
        assertThat(changes.size(), is(size));
        for (ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(true));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(change.get(OPERATIONS).asList().size(), is(1));
        }
        ModelNode currentChange = changes.get(0);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        ModelNode currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(ADD));
        assertThat(removePrefix(currentChangeOp).toString(), is(PathAddress.pathAddress(SYSTEM_PROPERTY, "slave").toString()));
        assertThat(currentChangeOp.get(VALUE).asString(), is("slave-config"));
        validateChanges(changes.subList(1, changes.size() - 1));
    }

    private void validateChanges(List<ModelNode> changes) throws IOException {
        for (ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(true));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(change.get(OPERATIONS).asList().size(), is(1));
        }
        ModelNode currentChange = changes.get(0);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        ModelNode currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(REMOVE));
        assertThat(removePrefix(currentChangeOp).toString(), is(IN_MEMORY_HANDLER_ADDRESS.toString()));

        currentChange = changes.get(1);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(removePrefix(currentChangeOp).toString(), is(IN_MEMORY_HANDLER_ADDRESS.toString()));
        assertThat(currentChangeOp.get(VALUE).asInt(), is(50));

        currentChange = changes.get(2);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(ADD));
        assertThat(removePrefix(currentChangeOp).toString(), is(IN_MEMORY_HANDLER_ADDRESS.toString()));

        currentChange = changes.get(3);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(REMOVE));
       assertThat(removePrefix(currentChangeOp).toString(), is(SYSTEM_PROPERTY_ADDRESS.toString()));

        currentChange = changes.get(4);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(removePrefix(currentChangeOp).toString(), is(AUDIT_LOG_ADDRESS.toString()));
        assertThat(currentChangeOp.get(VALUE).asBoolean(), is(true));

        currentChange = changes.get(5);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(UNDEFINE_ATTRIBUTE_OPERATION));
        assertThat(removePrefix(currentChangeOp).toString(), is(ALLOWED_ORIGINS_ADDRESS.toString()));

        assertThat(currentChangeOp.get(NAME).asString(), is(ALLOWED_ORIGINS));

        currentChange = changes.get(6);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(ADD));
        assertThat(removePrefix(currentChangeOp).toString(), is(SYSTEM_PROPERTY_ADDRESS.toString()));
        assertThat(currentChangeOp.get(VALUE).asString(), is("changeConfig"));

        currentChange = changes.get(7);
        assertThat(currentChange.get(OUTCOME).asString(), is(FAILED));
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(NAME).asString(), is(ALLOWED_ORIGINS));
    }

    private PathAddress removePrefix(ModelNode operation) {
        return PathAddress.pathAddress(operation.get(OP_ADDR)).subAddress(1);
    }

    public List<ModelNode> readConfigurationChanges(DomainClient client, PathElement prefix) throws IOException {
        PathAddress address = ADDRESS;
        if(prefix != null) {
            address = PathAddress.pathAddress().append(prefix).append(ADDRESS);
        }
        ModelNode readConfigChanges = Util.createOperation(ConfigurationChangeResourceDefinition.OPERATION_NAME, address);
        ModelNode response = client.execute(readConfigChanges);
        assertThat(response.asString(), response.get(OUTCOME).asString(), is(SUCCESS));
        logger.info("For " + prefix + " we have " + response.get(RESULT));
        return response.get(RESULT).asList();
    }

    private void setConfigurationChangeMaxHistory(DomainClient client, PathElement prefix, int size) throws IOException, UnsuccessfulOperationException {
        PathAddress address = PathAddress.pathAddress().append(prefix).append(ADDRESS);
        ModelNode writeMaxHistorySize = Util.getWriteAttributeOperation(address, MAX_HISTORY, size);
        ModelNode response = client.execute(writeMaxHistorySize);
        assertThat(response.asString(), response.get(OUTCOME).asString(), is(SUCCESS));
        checkMaxHistorySize(client, size, prefix);
    }

    private void checkMaxHistorySize(DomainClient client,  int expectedSize, PathElement ... prefix) throws UnsuccessfulOperationException {
        PathAddress address = PathAddress.pathAddress().append(prefix).append(ADDRESS);
        ModelNode readMaxHistorySize = Util.getReadAttributeOperation(address, MAX_HISTORY);
        ModelNode result = executeForResult(client, readMaxHistorySize);
        assertThat(result.asInt(), is(expectedSize));
    }

    public void clearConfigurationChanges(PathElement host) throws UnsuccessfulOperationException {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        final ModelNode remove = Util.createRemoveOperation(PathAddress.pathAddress().append(host).append(ADDRESS));
        executeForResult(client, remove);
    }

    public ModelNode executeForResult(final DomainClient client, final ModelNode operation) throws UnsuccessfulOperationException {
        try {
            final ModelNode result = client.execute(operation);
            checkSuccessful(result, operation);
            return result.get(RESULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkSuccessful(final ModelNode result,
                                 final ModelNode operation) throws UnsuccessfulOperationException {
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            logger.error("Operation " + operation + " did not succeed. Result was " + result);
            throw new UnsuccessfulOperationException(result.get(FAILURE_DESCRIPTION).toString());
        }
    }
}
