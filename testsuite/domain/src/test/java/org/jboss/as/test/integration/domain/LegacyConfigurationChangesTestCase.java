/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.USER_ID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOWED_ORIGINS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_DATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.List;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.LegacyConfigurationChangeResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class LegacyConfigurationChangesTestCase extends AbstractConfigurationChangesTestCase {

    @Test
    public void testConfigurationChanges() throws Exception {
        try {
            createConfigurationChanges(HOST_PRIMARY);
            createConfigurationChanges(HOST_SECONDARY);
            checkConfigurationChanges(readConfigurationChanges(domainPrimaryLifecycleUtil.getDomainClient(), HOST_PRIMARY), 11);
            checkConfigurationChanges(readConfigurationChanges(domainPrimaryLifecycleUtil.getDomainClient(), HOST_SECONDARY), 11);
            checkConfigurationChanges(readConfigurationChanges(domainSecondaryLifecycleUtil.getDomainClient(), HOST_SECONDARY), 11);
            checkRootConfigurationChangeWarning(domainPrimaryLifecycleUtil.getDomainClient());

            setConfigurationChangeMaxHistory(domainPrimaryLifecycleUtil.getDomainClient(), HOST_PRIMARY, 19);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 19, HOST_PRIMARY);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 19, HOST_PRIMARY, PathElement.pathElement(SERVER, "main-one"));
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SECONDARY);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SECONDARY, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSecondaryLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SECONDARY);
            checkMaxHistorySize(domainSecondaryLifecycleUtil.getDomainClient(), MAX_HISTORY_SIZE, HOST_SECONDARY, PathElement.pathElement(SERVER, "other-three"));

            setConfigurationChangeMaxHistory(domainPrimaryLifecycleUtil.getDomainClient(), HOST_SECONDARY, 20);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 20, HOST_SECONDARY);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 20, HOST_SECONDARY, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSecondaryLifecycleUtil.getDomainClient(), 20, HOST_SECONDARY);
            checkMaxHistorySize(domainSecondaryLifecycleUtil.getDomainClient(), 20, HOST_SECONDARY, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 19, HOST_PRIMARY);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 19, HOST_PRIMARY, PathElement.pathElement(SERVER, "main-one"));

            setConfigurationChangeMaxHistory(domainSecondaryLifecycleUtil.getDomainClient(), HOST_SECONDARY, 21);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 21, HOST_SECONDARY);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 21, HOST_SECONDARY, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSecondaryLifecycleUtil.getDomainClient(), 21, HOST_SECONDARY);
            checkMaxHistorySize(domainSecondaryLifecycleUtil.getDomainClient(), 21, HOST_SECONDARY, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 19, HOST_PRIMARY);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 19, HOST_PRIMARY, PathElement.pathElement(SERVER, "main-one"));
        } finally {
            clearConfigurationChanges(HOST_PRIMARY);
            clearConfigurationChanges(HOST_SECONDARY);
        }
    }

    @Test
    public void testConfigurationChangesOnSecondary() throws Exception {
        try {
            createConfigurationChanges(HOST_SECONDARY);
            PathAddress systemPropertyAddress = PathAddress.pathAddress().append(HOST_SECONDARY).append(SYSTEM_PROPERTY, "secondary");
            ModelNode setSystemProperty = Util.createAddOperation(systemPropertyAddress);
            setSystemProperty.get(VALUE).set("secondary-config");
            domainSecondaryLifecycleUtil.getDomainClient().execute(setSystemProperty);
            systemPropertyAddress = PathAddress.pathAddress().append(SERVER_GROUP, "main-server-group").append(SYSTEM_PROPERTY, "main");
            setSystemProperty = Util.createAddOperation(systemPropertyAddress);
            setSystemProperty.get(VALUE).set("main-config");
            domainPrimaryLifecycleUtil.getDomainClient().execute(setSystemProperty);
            List<ModelNode> changesOnSecondaryHC = readConfigurationChanges(domainSecondaryLifecycleUtil.getDomainClient(), HOST_SECONDARY);
            List<ModelNode> changesOnSecondaryDC = readConfigurationChanges(domainPrimaryLifecycleUtil.getDomainClient(), HOST_SECONDARY);
            checkSecondaryConfigurationChanges(changesOnSecondaryHC, 12);
            setConfigurationChangeMaxHistory(domainPrimaryLifecycleUtil.getDomainClient(), HOST_SECONDARY, 20);
            checkMaxHistorySize(domainPrimaryLifecycleUtil.getDomainClient(), 20, HOST_SECONDARY, PathElement.pathElement(SERVER, "other-three"));
            checkMaxHistorySize(domainSecondaryLifecycleUtil.getDomainClient(), 20, HOST_SECONDARY, PathElement.pathElement(SERVER, "other-three"));
            assertThat(changesOnSecondaryHC.size(), is(changesOnSecondaryDC.size()));
        } finally {
            clearConfigurationChanges(HOST_SECONDARY);
        }
    }

    @Test
    public void testEnablingConfigurationChangesOnHC() throws Exception {
        DomainClient client = domainSecondaryLifecycleUtil.getDomainClient();
        try {
            final ModelNode add = Util.createAddOperation(PathAddress.pathAddress().append(HOST_SECONDARY).append(getAddress()));
            add.get(LegacyConfigurationChangeResourceDefinition.MAX_HISTORY.getName()).set(MAX_HISTORY_SIZE);
            executeForResult(client, add);
        } finally {
            clearConfigurationChanges(HOST_SECONDARY);
        }
    }

    @Test
    public void testEnablingConfigurationChangesOnHC2() throws Exception {
        DomainClient client = domainSecondaryLifecycleUtil.getDomainClient();
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress().append(getAddress()));
        add.get(LegacyConfigurationChangeResourceDefinition.MAX_HISTORY.getName()).set(MAX_HISTORY_SIZE);
        ModelNode response = client.execute(add);
        assertThat(response.asString(), response.get(OUTCOME).asString(), is(FAILED));
        assertThat(response.get(FAILURE_DESCRIPTION).asString(), containsString("WFLYDC0032"));
    }

    @Test
    public void testNotSpecifiedHost() throws Exception {
        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress().append(getAddress()));
        add.get(LegacyConfigurationChangeResourceDefinition.MAX_HISTORY.getName()).set(MAX_HISTORY_SIZE);
        ModelNode response = client.execute(add);
        assertThat(response.asString(), response.get(OUTCOME).asString(), is(SUCCESS));
        assertThat(response.get(RESULT).asString(), containsString("WFLYDM0135"));
    }

    private void checkRootConfigurationChangeWarning(DomainClient client) throws IOException {
        PathAddress address = getAddress();
        ModelNode addConfigurationChanges = Util.createAddOperation(address);
        addConfigurationChanges.get(LegacyConfigurationChangeResourceDefinition.MAX_HISTORY.getName()).set(MAX_HISTORY_SIZE);
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
            // TODO Elytron - Restore capturing the Remote address.
            //assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(change.get(OPERATIONS).asList().size(), is(1));
        }
        validateChanges(changes);
    }

    private void checkSecondaryConfigurationChanges( List<ModelNode> changes, int size) throws IOException {
        assertThat(changes.size(), is(size));
        for (ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(true));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            // TODO Elytron - Restore capturing the Remote address.
            //assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
            assertThat(change.get(OPERATIONS).asList().size(), is(1));
        }
        ModelNode currentChange = changes.get(0);
        assertThat(currentChange.get(OUTCOME).asString(), is(SUCCESS));
        ModelNode currentChangeOp = currentChange.get(OPERATIONS).asList().get(0);
        assertThat(currentChangeOp.get(OP).asString(), is(ADD));
        assertThat(removePrefix(currentChangeOp).toString(), is(PathAddress.pathAddress(SYSTEM_PROPERTY, "secondary").toString()));
        assertThat(currentChangeOp.get(VALUE).asString(), is("secondary-config"));
        validateChanges(changes.subList(1, changes.size() - 1));
    }

    private void validateChanges(List<ModelNode> changes) throws IOException {
        for (ModelNode change : changes) {
            assertThat(change.hasDefined(OPERATION_DATE), is(true));
            assertThat(change.hasDefined(USER_ID), is(false));
            assertThat(change.hasDefined(DOMAIN_UUID), is(true));
            assertThat(change.hasDefined(ACCESS_MECHANISM), is(true));
            assertThat(change.get(ACCESS_MECHANISM).asString(), is("NATIVE"));
            // TODO Elytron - Restore capturing the Remote address.
            //assertThat(change.hasDefined(REMOTE_ADDRESS), is(true));
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

    @Override
    protected PathAddress getAddress() {
        return  PathAddress.pathAddress()
            .append(PathElement.pathElement(CORE_SERVICE, MANAGEMENT))
            .append(LegacyConfigurationChangeResourceDefinition.PATH);
    }
}
