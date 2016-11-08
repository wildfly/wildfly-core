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
package org.wildfly.core.test.standalone.mgmt.api.core;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOWED_ORIGINS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_DATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
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
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.ConfigurationChangeResourceDefinition;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test the configuration changes history command.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
@ServerSetup(ServerReload.SetupTask.class)
@RunWith(WildflyTestRunner.class)
public class ConfigurationChangesHistoryTestCase extends ContainerResourceMgmtTestBase {

    private static final int ALL_MAX_HISTORY_SIZE = 5;
    private static final int MAX_HISTORY_SIZE = 4;

    private static final PathAddress ALLOWED_ORIGINS_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(MANAGEMENT_INTERFACE, HTTP_INTERFACE);
    private static final PathAddress SYSTEM_PROPERTY_ADDRESS = PathAddress.pathAddress()
            .append(SYSTEM_PROPERTY, "test");
    private static final PathAddress ADDRESS = PathAddress.pathAddress()
            .append(PathElement.pathElement(CORE_SERVICE, MANAGEMENT))
            .append(ConfigurationChangeResourceDefinition.PATH);

    @Before
    public void setConfigurationChanges() throws Exception {
        ModelControllerClient client = getModelControllerClient();
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(ADDRESS));
        add.get(ConfigurationChangeResourceDefinition.MAX_HISTORY.getName()).set(MAX_HISTORY_SIZE);
        client.execute(add);
        createConfigurationChanges(client);
    }

    public void createConfigurationChanges(ModelControllerClient client) throws Exception {
        ModelNode setAllowedOrigins = Util.createEmptyOperation("list-add", ALLOWED_ORIGINS_ADDRESS);
        setAllowedOrigins.get(NAME).set(ALLOWED_ORIGINS);
        setAllowedOrigins.get(VALUE).set( "http://www.wildfly.org");
        client.execute(setAllowedOrigins);
        ModelNode setSystemProperty = Util.createAddOperation(SYSTEM_PROPERTY_ADDRESS);
        setSystemProperty.get(VALUE).set("changeConfig");
        client.execute(setSystemProperty);
        ModelNode unsetAllowedOrigins = Util.getUndefineAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ALLOWED_ORIGINS);
        client.execute(unsetAllowedOrigins);
        ModelNode unsetSystemProperty = Util.createRemoveOperation(SYSTEM_PROPERTY_ADDRESS);
        client.execute(unsetSystemProperty);
        //read
        client.execute(Util.getReadAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ALLOWED_ORIGINS));
        //invalid operation
        client.execute(Util.getUndefineAttributeOperation(ALLOWED_ORIGINS_ADDRESS, "not-exists-attribute"));
        //invalid operation
        client.execute(Util.getWriteAttributeOperation(ALLOWED_ORIGINS_ADDRESS, "not-exists-attribute", "123456"));
        //write operation, failed
        ModelNode setAllowedOriginsFails = Util.getWriteAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ALLOWED_ORIGINS, "123456");//wrong type, expected is LIST, op list-add
        client.execute(setAllowedOriginsFails);
    }

    @After
    public void clearConfigurationChanges() throws IOException {
        ModelControllerClient client = getModelControllerClient();
        final ModelNode remove = Util.createRemoveOperation(ADDRESS);
        client.execute(remove);
    }

    @Test
    public void testConfigurationChanges() throws Exception {
        final ModelNode listConfigurationChanges = Util.createOperation(ConfigurationChangeResourceDefinition.OPERATION_NAME, ADDRESS);
        List<ModelNode> changes = getManagementClient().executeForResult(listConfigurationChanges).asList();
        assertThat(changes.toString(), changes.size(), is(MAX_HISTORY_SIZE));
        for(ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(false));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            // TODO Elytron - Restore capturing the Remote address.
            //assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(change.get(OPERATIONS).asList().size(), is(1));
        }

        ModelNode currentChange = changes.get(0);
        ModelNode currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(ALLOWED_ORIGINS_ADDRESS.toModelNode().asString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(FAILED));
        currentChange = changes.get(1);
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(REMOVE));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChange = changes.get(2);
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(UNDEFINE_ATTRIBUTE_OPERATION));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(ALLOWED_ORIGINS_ADDRESS.toModelNode().asString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChange = changes.get(3);
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(ADD));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toModelNode().asString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
    }

    @Test
    public void testAllConfigurationChanges() throws Exception {
        final ModelNode update = Util.getWriteAttributeOperation(ADDRESS, ConfigurationChangeResourceDefinition.MAX_HISTORY.getName(), ALL_MAX_HISTORY_SIZE);
        getModelControllerClient().execute(update);
        final ModelNode listConfigurationChanges = Util.createOperation(ConfigurationChangeResourceDefinition.OPERATION_NAME, ADDRESS);
        List<ModelNode> changes = getManagementClient().executeForResult(listConfigurationChanges).asList();
        assertThat(changes.toString(), changes.size(), is(ALL_MAX_HISTORY_SIZE));
        for(ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(false));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            // TODO Elytron - Restore capturing the Remote address.
            //assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(change.get(OPERATIONS).asList().size(), is(1));
        }

        ModelNode currentChange = changes.get(0);
        ModelNode currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(ADDRESS.toModelNode().asString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChange = changes.get(1);
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(WRITE_ATTRIBUTE_OPERATION));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(ALLOWED_ORIGINS_ADDRESS.toModelNode().asString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(FAILED));
        currentChange = changes.get(2);
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(REMOVE));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChange = changes.get(3);
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(UNDEFINE_ATTRIBUTE_OPERATION));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(ALLOWED_ORIGINS_ADDRESS.toModelNode().asString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        currentChange = changes.get(4);
        currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(ADD));
        assertThat(currentChangeOp.get(OP_ADDR).asString(), is(SYSTEM_PROPERTY_ADDRESS.toModelNode().asString()));
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
    }
}
