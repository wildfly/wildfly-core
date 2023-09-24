/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
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
 * Unit test of {@link ApplicationTypeConstraint}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class ApplicationTypeConstraintUnitTestCase {

    private static final List<AccessConstraintDefinition> rootResourceConstraints = new ArrayList<AccessConstraintDefinition>();
    private static final List<AccessConstraintDefinition> childResourceConstraints = new ArrayList<AccessConstraintDefinition>();

    private static final ApplicationTypeConfig a = new ApplicationTypeConfig("test", "a", false);
    private static final ApplicationTypeConfig b = new ApplicationTypeConfig("test", "b", false);


    private static final ApplicationTypeAccessConstraintDefinition atacda = new ApplicationTypeAccessConstraintDefinition(a);
    private static final ApplicationTypeAccessConstraintDefinition atacdb = new ApplicationTypeAccessConstraintDefinition(b);

    private static final OperationDefinition WRITE_CONFIG_DEF = new SimpleOperationDefinitionBuilder("write-config", NonResolvingResourceDescriptionResolver.INSTANCE)
            .build();

    private static final Constraint DEPLOYER_WRITE_CONFIG = ApplicationTypeConstraint.FACTORY.getStandardUserConstraint(StandardRole.DEPLOYER, Action.ActionEffect.WRITE_CONFIG);
    private static final Constraint ADMIN_WRITE_CONFIG = ApplicationTypeConstraint.FACTORY.getStandardUserConstraint(StandardRole.ADMINISTRATOR, Action.ActionEffect.WRITE_CONFIG);

    private TargetResource rootTarget;
    private TargetResource childTarget;

    @Before
    public void setUp() {
        setupResources(false, false);
    }

    private void setupResources(boolean isA, boolean isB) {

        a.setConfiguredApplication(isA);
        b.setConfiguredApplication(isB);

        ResourceDefinition rootRd = new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE) {
            @Override
            public List<AccessConstraintDefinition> getAccessConstraints() {
                return rootResourceConstraints;
            }
        };
        ManagementResourceRegistration rootRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(rootRd);
        rootRegistration.registerOperationHandler(WRITE_CONFIG_DEF, NoopOperationStepHandler.WITHOUT_RESULT, true);

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
        childResourceConstraints.add(atacda);
        childResourceConstraints.add(atacdb);

        multipleConsistentTest();
    }

    @Test
    public void testMultipleInconsistentConstraints() {
        rootResourceConstraints.add(atacda);
        rootResourceConstraints.add(atacdb);

        multipleInconsistentTest();
    }

    @Test
    public void testInheritedConsistentConstraints() {
        rootResourceConstraints.add(atacda);
        childResourceConstraints.add(atacdb);

        multipleConsistentTest();
    }

    @Test
    public void testInheritedInconsistentConstraints() {
        rootResourceConstraints.add(atacda);
        childResourceConstraints.add(atacdb);

        multipleInconsistentTest();
    }

    private void multipleConsistentTest() {

        Constraint testee = ApplicationTypeConstraint.FACTORY.getRequiredConstraint(Action.ActionEffect.WRITE_CONFIG, getWriteConfigAction(), childTarget);
        assertTrue(DEPLOYER_WRITE_CONFIG.violates(testee, Action.ActionEffect.WRITE_CONFIG));
        assertTrue(testee.violates(DEPLOYER_WRITE_CONFIG, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(ADMIN_WRITE_CONFIG.violates(testee, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(testee.violates(ADMIN_WRITE_CONFIG, Action.ActionEffect.WRITE_CONFIG));

        setupResources(true, true);

        testee = ApplicationTypeConstraint.FACTORY.getRequiredConstraint(Action.ActionEffect.WRITE_CONFIG, getWriteConfigAction(), childTarget);
        assertFalse(DEPLOYER_WRITE_CONFIG.violates(testee, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(testee.violates(DEPLOYER_WRITE_CONFIG, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(ADMIN_WRITE_CONFIG.violates(testee, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(testee.violates(ADMIN_WRITE_CONFIG, Action.ActionEffect.WRITE_CONFIG));
    }

    private void multipleInconsistentTest() {

        setupResources(false, true);

        Constraint testee = ApplicationTypeConstraint.FACTORY.getRequiredConstraint(Action.ActionEffect.WRITE_CONFIG, getWriteConfigAction(), childTarget);
        assertFalse(DEPLOYER_WRITE_CONFIG.violates(testee, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(testee.violates(DEPLOYER_WRITE_CONFIG, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(ADMIN_WRITE_CONFIG.violates(testee, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(testee.violates(ADMIN_WRITE_CONFIG, Action.ActionEffect.WRITE_CONFIG));

        setupResources(true, false);

        testee = ApplicationTypeConstraint.FACTORY.getRequiredConstraint(Action.ActionEffect.WRITE_CONFIG, getWriteConfigAction(), childTarget);
        assertFalse(DEPLOYER_WRITE_CONFIG.violates(testee, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(testee.violates(DEPLOYER_WRITE_CONFIG, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(ADMIN_WRITE_CONFIG.violates(testee, Action.ActionEffect.WRITE_CONFIG));
        assertFalse(testee.violates(ADMIN_WRITE_CONFIG, Action.ActionEffect.WRITE_CONFIG));

    }

    private Action getWriteConfigAction() {
        OperationEntry oe = rootTarget.getResourceRegistration().getOperationEntry(PathAddress.EMPTY_ADDRESS, "write-config");
        ModelNode op = Util.createEmptyOperation("write-config", null);
        return new Action(op, oe, EnumSet.of(Action.ActionEffect.WRITE_CONFIG));
    }
}
