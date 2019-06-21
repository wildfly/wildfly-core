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
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.USER_ID;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.REMOTE_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOWED_ORIGINS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURATION_CHANGES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_DATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ConfigurationChangesTestCase extends AbstractConfigurationChangesTestCase {

    private static final PathElement DEFAULT_PROFILE = PathElement.pathElement(PROFILE, "default");
    private static final PathElement OTHER_PROFILE = PathElement.pathElement(PROFILE, "other");

    @Test
    public void testConfigurationChanges() throws Exception {
        try {
            // These first two changes don't get recorded on hosts because the host subsystem is not installed yet
            createProfileConfigurationChange(DEFAULT_PROFILE, MAX_HISTORY_SIZE); // shouldn't appear on slave
            createProfileConfigurationChange(OTHER_PROFILE, MAX_HISTORY_SIZE);

            createConfigurationChanges(HOST_MASTER);
            createConfigurationChanges(HOST_SLAVE);
            checkConfigurationChanges(readConfigurationChanges(domainMasterLifecycleUtil.getDomainClient(), HOST_MASTER),
                    11, Collections.singleton(10)); // the first op from createConfigurationChanges is
                                                         // non multi-process and gets no domain-uuid
            checkConfigurationChanges(readConfigurationChanges(domainMasterLifecycleUtil.getDomainClient(), HOST_SLAVE),
                    11, Collections.emptySet()); // All ops routed from master to slave get a domain-uuid
            checkConfigurationChanges(readConfigurationChanges(domainSlaveLifecycleUtil.getDomainClient(), HOST_SLAVE),
                    11, Collections.emptySet()); // All ops routed from master to slave get a domain-uuid

            setConfigurationChangeMaxHistory(domainMasterLifecycleUtil.getDomainClient(), HOST_MASTER, 19);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 19, HOST_MASTER);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_MASTER, PathElement.pathElement(SERVER, "main-one"));
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SLAVE);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSlaveLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SLAVE);
            checkMaxHistorySize(domainSlaveLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));

            setConfigurationChangeMaxHistory(domainMasterLifecycleUtil.getDomainClient(), HOST_SLAVE, 20);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 20, HOST_SLAVE);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSlaveLifecycleUtil.getDomainClient(), 20, HOST_SLAVE);
            checkMaxHistorySize(domainSlaveLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 19, HOST_MASTER);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_MASTER, PathElement.pathElement(SERVER, "main-one"));

            setConfigurationChangeMaxHistory(domainMasterLifecycleUtil.getDomainClient(), OTHER_PROFILE, 21);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 20, HOST_SLAVE);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 21, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSlaveLifecycleUtil.getDomainClient(), 20, HOST_SLAVE);
            checkMaxHistorySize(domainSlaveLifecycleUtil.getDomainClient(), 21, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 19, HOST_MASTER);
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_MASTER, PathElement.pathElement(SERVER, "main-one"));
        } finally {
            clearProfileConfigurationChange(OTHER_PROFILE);
            clearProfileConfigurationChange(DEFAULT_PROFILE);
            clearConfigurationChanges(HOST_SLAVE);
            clearConfigurationChanges(HOST_MASTER);
        }
    }

    @Test
    public void testConfigurationChangesOnSlave() throws Exception {
        try {
            createProfileConfigurationChange(OTHER_PROFILE, 20);
            createConfigurationChanges(HOST_SLAVE);
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
            checkMaxHistorySize(domainMasterLifecycleUtil.getDomainClient(), 20, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSlaveLifecycleUtil.getDomainClient(), 20, HOST_SLAVE, PathElement.pathElement(SERVER, "other-three"));
            assertThat(changesOnSlaveHC.size(), is(changesOnSlaveDC.size()));
        } finally {
            clearProfileConfigurationChange(OTHER_PROFILE);
            clearConfigurationChanges(HOST_SLAVE);
        }
    }

    @Test
    public void testExcludeLegacyOnHost() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        try {
            createProfileConfigurationChange(OTHER_PROFILE, 5);
            createConfigurationChanges(HOST_SLAVE);
            final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(PathAddress.pathAddress()
                    .append(HOST_SLAVE)
                    .append(CORE_SERVICE, MANAGEMENT)
                    .append(SERVICE, CONFIGURATION_CHANGES)));
            add.get("max-history").set(MAX_HISTORY_SIZE);
            ModelNode response = client.execute(add);
            Assert.assertFalse(response.toString(), Operations.isSuccessfulOutcome(response));
            // Here we don't expect the failure message to reflect duplicate capability registration
            // because we've registered the cap in 3 different scopes
            // 1) the /profile=other-profile stuff is in ProfileChildCapabilityScope
            // 2) the /host=slave/subsysytem=core-management stuff is in HostCapabilityScope
            // 3) the /host=slave/core-service=management stuff is in CapabilityScope.GLOBAL
            // That means there is no conflict on the HC. No conflict on the server the HC failure means
            // it never reaches the server.
            assertThat(Operations.getFailureDescription(response).asString(), containsString("WFLYCTL0158"));
        } finally {
            clearProfileConfigurationChange(OTHER_PROFILE);
            clearConfigurationChanges(HOST_SLAVE);
            client.execute(Util.createRemoveOperation(PathAddress.pathAddress(PathAddress.pathAddress()
                    .append(HOST_SLAVE)
                    .append(CORE_SERVICE, MANAGEMENT)
                    .append(SERVICE, CONFIGURATION_CHANGES))));
        }
    }

    @Test
    public void testExcludeLegacyOnManagedServers() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        try {
            createProfileConfigurationChange(OTHER_PROFILE, 5);
            createConfigurationChanges(HOST_SLAVE);
            clearConfigurationChanges(HOST_SLAVE);
            final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(PathAddress.pathAddress()
                    .append(HOST_SLAVE)
                    .append(CORE_SERVICE, MANAGEMENT)
                    .append(SERVICE, CONFIGURATION_CHANGES)));
            add.get("max-history").set(MAX_HISTORY_SIZE);
            ModelNode response = client.execute(add);
            Assert.assertFalse(response.toString(), Operations.isSuccessfulOutcome(response));
            assertThat(Operations.getFailureDescription(response).asString(), containsString("WFLYCTL0436"));
        } finally {
            clearProfileConfigurationChange(OTHER_PROFILE);
            client.execute(Util.createRemoveOperation(PathAddress.pathAddress(PathAddress.pathAddress()
                    .append(HOST_SLAVE)
                    .append(CORE_SERVICE, MANAGEMENT)
                    .append(SERVICE, CONFIGURATION_CHANGES))));
        }
    }

    @Test
    public void testExcludeByLegacy() throws Exception{
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        try {
            final ModelNode add = Util.createAddOperation(PathAddress.pathAddress(PathAddress.pathAddress()
                    .append(HOST_SLAVE)
                    .append(CORE_SERVICE, MANAGEMENT)
                    .append(SERVICE, CONFIGURATION_CHANGES)));
            add.get("max-history").set(MAX_HISTORY_SIZE);
            executeForResult(client, add);

            final ModelNode addToProfile = Util.createAddOperation(PathAddress.pathAddress().append(OTHER_PROFILE).append(getAddress()));
            add.get("max-history").set(1);
            ModelNode response = client.execute(addToProfile);
            Assert.assertFalse(response.toString(), Operations.isSuccessfulOutcome(response));
            // This fails with a duplicate capability message on the server
            assertThat(Operations.getFailureDescription(response).asString(), containsString("WFLYCTL0436"));

            final ModelNode addToSlave = Util.createAddOperation(PathAddress.pathAddress().append(HOST_SLAVE).append(getAddress()));
            add.get("max-history").set(1);
            response = client.execute(addToSlave);
            Assert.assertFalse(response.toString(), Operations.isSuccessfulOutcome(response));
            // Here we don't expect the failure message to reflect duplicate capability registration
            // because we've registered the cap in 2 different scopes
            // 1) the /host=slave/core-service=management stuff is in CapabilityScope.GLOBAL
            // 2) the /host=slave/subsysytem=core-management stuff is in HostCapabilityScope
            assertThat(Operations.getFailureDescription(response).asString(), containsString("WFLYCTL0158"));

        } finally {
            client.execute(Util.createRemoveOperation(PathAddress.pathAddress(PathAddress.pathAddress()
                    .append(HOST_SLAVE)
                    .append(CORE_SERVICE, MANAGEMENT)
                    .append(SERVICE, CONFIGURATION_CHANGES))));
        }
    }

    @Test
    public void testEnablingConfigurationChangesOnHC() throws Exception {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        try {
            final ModelNode add = Util.createAddOperation(PathAddress.pathAddress().append(HOST_SLAVE).append(getAddress()));
            add.get("max-history").set(MAX_HISTORY_SIZE);
            executeForResult(client, add);
        } finally {
            clearConfigurationChanges(HOST_SLAVE);
        }
    }

    private void checkConfigurationChanges(List<ModelNode> changes, int size, Set<Integer> localOnly) throws IOException {
        assertThat(changes.toString(), changes.size(), is(size));
        for (int i = 0; i < size; i++) {
            ModelNode change = changes.get(i);
            String msg = i + " -- " + changes.toString();
            assertThat(msg, change.hasDefined(OPERATION_DATE), is(true));
            assertThat(msg, change.hasDefined(USER_ID), is(false));
            assertThat(msg, change.hasDefined(DOMAIN_UUID), is(!localOnly.contains(i)));
            assertThat(msg, change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(msg, change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            assertThat(msg, change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(msg, change.get(OPERATIONS).asList().size(), is(1));
        }
        validateChanges(changes);
    }

    private void checkSlaveConfigurationChanges( List<ModelNode> changes, int size) throws IOException {
        assertThat(changes.toString(),changes.size(), is(size));
        for (ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(true)); // all the slave changes have a domain-uuid,
                                                                        // either due to routing from the master or
                                                                        // due to local need for rollout to servers
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

    private void createProfileConfigurationChange(PathElement profile, int maxHistory) throws IOException, UnsuccessfulOperationException {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress().append(profile).append(getAddress()));
        add.get("max-history").set(maxHistory);
        executeForResult(client, add);
    }

    private void clearProfileConfigurationChange(PathElement profile) throws IOException, UnsuccessfulOperationException {
        DomainClient client = domainMasterLifecycleUtil.getDomainClient();
        final ModelNode remove = Util.createRemoveOperation(PathAddress.pathAddress().append(profile).append(getAddress()));
        executeForResult(client, remove);
    }

    @Override
    protected PathAddress getAddress() {
        return PathAddress.pathAddress()
            .append(PathElement.pathElement(SUBSYSTEM, "core-management"))
            .append(PathElement.pathElement("service", "configuration-changes"));
    }
}
