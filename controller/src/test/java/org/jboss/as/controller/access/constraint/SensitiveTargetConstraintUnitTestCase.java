/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jboss.as.controller.NoopOperationStepHandler;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.access.rbac.StandardRole;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test of {@link SensitiveTargetConstraint}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SensitiveTargetConstraintUnitTestCase {

    private static final List<AccessConstraintDefinition> rootResourceConstraints = new ArrayList<AccessConstraintDefinition>();
    private static final List<AccessConstraintDefinition> childResourceConstraints = new ArrayList<AccessConstraintDefinition>();

    private static final SensitivityClassification a = new SensitivityClassification("test", "a", false, false, false);
    private static final SensitivityClassification b = new SensitivityClassification("test", "b", false, false, false);


    private static final SensitiveTargetAccessConstraintDefinition stacda = new SensitiveTargetAccessConstraintDefinition(a);
    private static final SensitiveTargetAccessConstraintDefinition stacdb = new SensitiveTargetAccessConstraintDefinition(b);

    private static final OperationDefinition READ_CONFIG_DEF = new SimpleOperationDefinitionBuilder("read-config", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setReadOnly()
            .build();

    private static final Constraint MONITOR_READ_CONFIG = SensitiveTargetConstraint.FACTORY.getStandardUserConstraint(StandardRole.MONITOR, Action.ActionEffect.READ_CONFIG);
    private static final Constraint ADMIN_READ_CONFIG = SensitiveTargetConstraint.FACTORY.getStandardUserConstraint(StandardRole.ADMINISTRATOR, Action.ActionEffect.READ_CONFIG);

    private TargetResource rootTarget;
    private TargetResource childTarget;

    @Before
    public void setUp() {
        a.setConfiguredRequiresAccessPermission(false);
        b.setConfiguredRequiresAccessPermission(false);
        a.setConfiguredRequiresReadPermission(false);
        b.setConfiguredRequiresReadPermission(false);
        a.setConfiguredRequiresWritePermission(false);
        b.setConfiguredRequiresWritePermission(false);
        setupResources();
    }

    private void setupResources() {
        ResourceDefinition rootRd = new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE) {
            @Override
            public List<AccessConstraintDefinition> getAccessConstraints() {
                return rootResourceConstraints;
            }
        };
        ManagementResourceRegistration rootRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(rootRd);
        rootRegistration.registerOperationHandler(READ_CONFIG_DEF, NoopOperationStepHandler.WITH_RESULT, true);
        PathElement childPE = PathElement.pathElement("child");
        ResourceDefinition childRd = new SimpleResourceDefinition(childPE, NonResolvingResourceDescriptionResolver.INSTANCE) {
            @Override
            public List<AccessConstraintDefinition> getAccessConstraints() {
                return childResourceConstraints;
            }
        };
        ManagementResourceRegistration childRegistration = rootRegistration.registerSubModel(childRd);

        rootTarget = TargetResource.forStandalone(PathAddress.EMPTY_ADDRESS, rootRegistration, Resource.Factory.create());
        childTarget = TargetResource.forStandalone(PathAddress.pathAddress(childPE), childRegistration, Resource.Factory.create());
    }

    @After
    public void tearDown() {
        rootResourceConstraints.clear();
        childResourceConstraints.clear();
    }

    @Test
    public void testMultipleConsistentConstraints() {
        childResourceConstraints.add(stacda);
        childResourceConstraints.add(stacdb);

        multipleConsistentTest();
    }

    @Test
    public void testMultipleInconsistentConstraints() {
        rootResourceConstraints.add(stacda);
        rootResourceConstraints.add(stacdb);

        multipleInconsistentTest();
    }

    @Test
    public void testInheritedConsistentConstraints() {
        rootResourceConstraints.add(stacda);
        childResourceConstraints.add(stacdb);

        multipleConsistentTest();
    }

    @Test
    public void testInheritedInconsistentConstraints() {
        rootResourceConstraints.add(stacda);
        childResourceConstraints.add(stacdb);

        multipleInconsistentTest();
    }

    private void multipleConsistentTest() {

        Constraint testee = SensitiveTargetConstraint.FACTORY.getRequiredConstraint(Action.ActionEffect.READ_CONFIG, getReadConfigAction(), childTarget);
        assertFalse(MONITOR_READ_CONFIG.violates(testee, Action.ActionEffect.READ_CONFIG));
        assertFalse(testee.violates(MONITOR_READ_CONFIG, Action.ActionEffect.READ_CONFIG));
        assertFalse(ADMIN_READ_CONFIG.violates(testee, Action.ActionEffect.READ_CONFIG));
        assertFalse(testee.violates(ADMIN_READ_CONFIG, Action.ActionEffect.READ_CONFIG));

        a.setConfiguredRequiresReadPermission(true);
        b.setConfiguredRequiresReadPermission(true);
        setupResources();

        testee = SensitiveTargetConstraint.FACTORY.getRequiredConstraint(Action.ActionEffect.READ_CONFIG, getReadConfigAction(), childTarget);
        assertTrue(MONITOR_READ_CONFIG.violates(testee, Action.ActionEffect.READ_CONFIG));
        assertTrue(testee.violates(MONITOR_READ_CONFIG, Action.ActionEffect.READ_CONFIG));
        assertFalse(ADMIN_READ_CONFIG.violates(testee, Action.ActionEffect.READ_CONFIG));
        assertFalse(testee.violates(ADMIN_READ_CONFIG, Action.ActionEffect.READ_CONFIG));
    }

    private void multipleInconsistentTest() {

        b.setConfiguredRequiresReadPermission(true);
        setupResources();

        Constraint testee = SensitiveTargetConstraint.FACTORY.getRequiredConstraint(Action.ActionEffect.READ_CONFIG, getReadConfigAction(), childTarget);
        assertTrue(MONITOR_READ_CONFIG.violates(testee, Action.ActionEffect.READ_CONFIG));
        assertTrue(testee.violates(MONITOR_READ_CONFIG, Action.ActionEffect.READ_CONFIG));
        assertFalse(ADMIN_READ_CONFIG.violates(testee, Action.ActionEffect.READ_CONFIG));
        assertFalse(testee.violates(ADMIN_READ_CONFIG, Action.ActionEffect.READ_CONFIG));

        a.setConfiguredRequiresReadPermission(true);
        b.setConfiguredRequiresReadPermission(false);
        setupResources();

        testee = SensitiveTargetConstraint.FACTORY.getRequiredConstraint(Action.ActionEffect.READ_CONFIG, getReadConfigAction(), childTarget);
        assertTrue(MONITOR_READ_CONFIG.violates(testee, Action.ActionEffect.READ_CONFIG));
        assertTrue(testee.violates(MONITOR_READ_CONFIG, Action.ActionEffect.READ_CONFIG));
        assertFalse(ADMIN_READ_CONFIG.violates(testee, Action.ActionEffect.READ_CONFIG));
        assertFalse(testee.violates(ADMIN_READ_CONFIG, Action.ActionEffect.READ_CONFIG));

    }

    private Action getReadConfigAction() {
        OperationEntry oe = rootTarget.getResourceRegistration().getOperationEntry(PathAddress.EMPTY_ADDRESS, "read-config");
        ModelNode op = Util.createEmptyOperation("read-config", null);
        return new Action(op, oe, EnumSet.of(Action.ActionEffect.READ_CONFIG));
    }
}
