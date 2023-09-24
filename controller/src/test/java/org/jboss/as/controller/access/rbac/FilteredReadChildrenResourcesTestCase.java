/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import static org.jboss.as.controller.PathAddress.EMPTY_ADDRESS;
import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition.Parameters;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests filtering of output from {@code read-resource} requests.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class FilteredReadChildrenResourcesTestCase extends AbstractRbacTestBase {
    public static final String UNCONSTRAINED_RESOURCE = "unconstrained-resource";
    public static final String SENSITIVE_CONSTRAINED_RESOURCE = "sensitive-constrained-resource";

    public static final String FOO = "foo";
    public static final String BAR = "bar";

    @Before
    public void setup() {
        ModelNode operation = Util.createOperation(ADD, pathAddress(UNCONSTRAINED_RESOURCE, FOO));
        executeWithRoles(operation, StandardRole.SUPERUSER);
        operation = Util.createOperation(ADD, pathAddress(UNCONSTRAINED_RESOURCE, BAR));
        executeWithRoles(operation, StandardRole.SUPERUSER);

        operation = Util.createOperation(ADD, pathAddress(SENSITIVE_CONSTRAINED_RESOURCE, FOO));
        executeWithRoles(operation, StandardRole.SUPERUSER);
        operation = Util.createOperation(ADD, pathAddress(SENSITIVE_CONSTRAINED_RESOURCE, BAR));
        executeWithRoles(operation, StandardRole.SUPERUSER);
    }

    @Test
    public void testMonitor() {
        test(false, StandardRole.MONITOR);
    }

    @Test
    public void testOperator() {
        test(false, StandardRole.OPERATOR);
    }

    @Test
    public void testMaintainer() {
        test(false, StandardRole.MAINTAINER);
    }

    @Test
    public void testAdministrator() {
        test(true, StandardRole.ADMINISTRATOR);
    }

    @Test
    public void testAuditor() {
        test(true, StandardRole.AUDITOR);
    }

    @Test
    public void testSuperuser() {
        test(true, StandardRole.SUPERUSER);
    }

    @Test
    public void testMonitorOperator() {
        test(false, StandardRole.MONITOR, StandardRole.OPERATOR);
    }

    @Test
    public void testMonitorAdministrator() {
        test(true, StandardRole.MONITOR, StandardRole.ADMINISTRATOR);
    }

    @Test
    public void testAdministratorAuditor() {
        test(true, StandardRole.ADMINISTRATOR, StandardRole.AUDITOR);
    }

    private void test(boolean sensitiveResourceVisible, StandardRole... roles) {
        ModelNode operation = Util.createOperation(READ_CHILDREN_RESOURCES_OPERATION, EMPTY_ADDRESS);
        operation.get(CHILD_TYPE).set(UNCONSTRAINED_RESOURCE);
        ModelNode result = executeWithRoles(operation, roles);

        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.get(RESULT).hasDefined(FOO));
        assertTrue(result.get(RESULT).hasDefined(BAR));

        operation.get(CHILD_TYPE).set(SENSITIVE_CONSTRAINED_RESOURCE);
        result = executeWithRoles(operation, roles);
        assertEquals(sensitiveResourceVisible, result.get(RESULT).has(FOO));
        assertEquals(sensitiveResourceVisible, result.get(RESULT).has(BAR));

        // lthon asks: is this format stable? testing it isn't that important anyway...
        // BES 2013/07/08 Yes, it's stable and needs testing as automated clients will be relying on it
        assertEquals(!sensitiveResourceVisible, result.get(RESPONSE_HEADERS, ACCESS_CONTROL).get(0)
                .get("filtered-children-types").get(0).asString().equals(SENSITIVE_CONSTRAINED_RESOURCE));
    }

    // model definition

    private static final SensitivityClassification MY_SENSITIVITY
            = new SensitivityClassification("test", "my-sensitivity", true, true, true);
    private static final AccessConstraintDefinition MY_SENSITIVE_CONSTRAINT
            = new SensitiveTargetAccessConstraintDefinition(MY_SENSITIVITY);

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, ProcessType.EMBEDDED_SERVER);

        GlobalNotifications.registerGlobalNotifications(registration, ProcessType.EMBEDDED_SERVER);

        registration.registerSubModel(new SimpleResourceDefinition(
                new Parameters(pathElement(UNCONSTRAINED_RESOURCE), NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(new AbstractAddStepHandler() {})
                    .setRemoveHandler(new AbstractRemoveStepHandler() {})));
        registration.registerSubModel(new SimpleResourceDefinition(
                new Parameters(pathElement(SENSITIVE_CONSTRAINED_RESOURCE), NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(new AbstractAddStepHandler() {})
                    .setRemoveHandler(new AbstractRemoveStepHandler() {})
                    .setAccessConstraints(MY_SENSITIVE_CONSTRAINT)));
    }
}
