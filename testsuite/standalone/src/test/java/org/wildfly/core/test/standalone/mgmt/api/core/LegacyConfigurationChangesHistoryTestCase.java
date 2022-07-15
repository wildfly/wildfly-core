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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.REMOTE_ADDRESS;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.USER_ID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_DATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.LegacyConfigurationChangeResourceDefinition;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test the configuration changes history command.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
@ServerSetup(ServerReload.SetupTask.class)
@RunWith(WildFlyRunner.class)
public class LegacyConfigurationChangesHistoryTestCase extends AbstractConfigurationChangesTestCase {

    private static final PathAddress ADDRESS = PathAddress.pathAddress()
            .append(PathElement.pathElement(CORE_SERVICE, MANAGEMENT))
            .append(LegacyConfigurationChangeResourceDefinition.PATH);

    @Before
    public void setConfigurationChanges() throws Exception {
        ModelControllerClient client = getModelControllerClient();
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(ADDRESS));
        add.get("max-history").set(MAX_HISTORY_SIZE);
        getManagementClient().executeForResult(add);
        createConfigurationChanges(client);
    }

    @After
    public void clearConfigurationChanges() throws IOException {
        ModelControllerClient client = getModelControllerClient();
        final ModelNode remove = Util.createRemoveOperation(ADDRESS);
        client.execute(remove);
    }

    @Test
    public void testConfigurationChanges() throws Exception {
        final ModelNode listConfigurationChanges = Util.createOperation("list-changes", ADDRESS);
        List<ModelNode> changes = getManagementClient().executeForResult(listConfigurationChanges).asList();
        assertThat(changes.toString(), changes.size(), is(MAX_HISTORY_SIZE));
        for(ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(false));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
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
        final ModelNode update = Util.getWriteAttributeOperation(ADDRESS, "max-history", ALL_MAX_HISTORY_SIZE);
        getModelControllerClient().execute(update);
        final ModelNode listConfigurationChanges = Util.createOperation("list-changes", ADDRESS);
        List<ModelNode> changes = getManagementClient().executeForResult(listConfigurationChanges).asList();
        assertThat(changes.toString(), changes.size(), is(ALL_MAX_HISTORY_SIZE));
        for(ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(false));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
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

    @Test
    public void testExcludeSubSystem() throws Exception {
        ModelControllerClient client = getModelControllerClient();
        try {
            final ModelNode listConfigurationChanges = Util.createOperation("list-changes", ADDRESS);
            List<ModelNode> changes = getManagementClient().executeForResult(listConfigurationChanges).asList();
            assertThat(changes.toString(), changes.size(), is(MAX_HISTORY_SIZE));
            final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(PathAddress.pathAddress()
                    .append(PathElement.pathElement(SUBSYSTEM, "core-management"))
                    .append(PathElement.pathElement("service", "configuration-changes"))));
            add.get("max-history").set(MAX_HISTORY_SIZE);
            ModelNode response = client.execute(add);
            Assert.assertFalse(response.toString(), Operations.isSuccessfulOutcome(response));
            assertThat(Operations.getFailureDescription(response).asString(), containsString("WFLYCTL0436"));
        } finally {
            client.execute(Util.createRemoveOperation(PathAddress.pathAddress(PathAddress.pathAddress()
                    .append(PathElement.pathElement(SUBSYSTEM, "core-management"))
                    .append(PathElement.pathElement("service", "configuration-changes")))));
        }
    }
}
