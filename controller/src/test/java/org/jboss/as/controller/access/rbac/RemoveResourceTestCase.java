/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access.rbac;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.constraint.VaultExpressionSensitivityConfig;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

/**
 * Tests access control of resource remove.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class RemoveResourceTestCase extends AbstractControllerTestBase {

    private static final PathElement ONE = PathElement.pathElement("one");
    private static final PathElement ONE_A = PathElement.pathElement("one", "a");
    private static final PathElement ONE_B = PathElement.pathElement("one", "b");
    private static final PathAddress ONE_B_ADDR = PathAddress.pathAddress(ONE_B);

    private static final SensitiveTargetAccessConstraintDefinition WRITE_CONSTRAINT = new SensitiveTargetAccessConstraintDefinition(
            new SensitivityClassification("test", "test", false, false, true));

    private volatile ManagementResourceRegistration rootRegistration;
    private volatile Resource rootResource;

    @Test
    public void testMonitorRemoveNoSensitivity() throws Exception {
        testRemoveNoSensitivity(StandardRole.MONITOR, false);
    }

    @Test
    public void testMaintainerRemoveNoSensitivity() throws Exception {
        testRemoveNoSensitivity(StandardRole.MAINTAINER, true);
    }

    @Test
    public void testAdministratorRemoveNoSensitivity() throws Exception {
        testRemoveNoSensitivity(StandardRole.ADMINISTRATOR, true);
    }

    private void testRemoveNoSensitivity(StandardRole role, boolean success) throws Exception {
        ChildResourceDefinition def = new ChildResourceDefinition(ONE);
        def.addAttribute("test");
        rootRegistration.registerSubModel(def);

        Resource resourceA = Resource.Factory.create();
        resourceA.getModel().get("test").set("a");
        rootResource.registerChild(ONE_A, resourceA);

        Resource resourceB = Resource.Factory.create();
        resourceB.getModel().get("test").set("b");
        rootResource.registerChild(ONE_B, resourceB);

        ModelNode op = Util.createRemoveOperation(ONE_B_ADDR);
        op.get(OPERATION_HEADERS, "roles").set(role.toString());
        if (success) {
            executeForResult(op);
        } else {
            executeForFailure(op);
        }
    }

    @Test
    public void testMonitorRemoveWithWriteAttributeSensitivity() throws Exception {
        testRemoveWithWriteAttributeSensitivity(StandardRole.MONITOR, false);
    }

    @Test
    public void testMaintainerRemoveWithWriteAttributeSensitivity() throws Exception {
        testRemoveWithWriteAttributeSensitivity(StandardRole.MAINTAINER, true);
    }

    @Test
    public void testAdministratorRemoveWithWriteAttributeSensitivity() throws Exception {
        testRemoveWithWriteAttributeSensitivity(StandardRole.ADMINISTRATOR, true);
    }

    private void testRemoveWithWriteAttributeSensitivity(StandardRole role, boolean success) throws Exception {
        ChildResourceDefinition def = new ChildResourceDefinition(ONE);
        def.addAttribute("test", WRITE_CONSTRAINT);
        rootRegistration.registerSubModel(def);

        Resource resourceA = Resource.Factory.create();
        resourceA.getModel().get("test").set("a");
        rootResource.registerChild(ONE_A, resourceA);

        Resource resourceB = Resource.Factory.create();
        resourceB.getModel().get("test").set("b");
        rootResource.registerChild(ONE_B, resourceB);

        ModelNode op = Util.createRemoveOperation(ONE_B_ADDR);
        op.get(OPERATION_HEADERS, "roles").set(role.toString());
        if (success) {
            executeForResult(op);
        } else {
            executeForFailure(op);
        }
    }


    @Test
    public void testMonitorRemoveWithVaultWriteSensitivity() throws Exception {
        testRemoveWithVaultWriteSensitivity(StandardRole.MONITOR, false);
    }

    @Test
    public void testMaintainerRemoveWithVaultWriteSensitivity() throws Exception {
        testRemoveWithVaultWriteSensitivity(StandardRole.MAINTAINER, true);
    }

    @Test
    public void testAdministratorRemoveWithVaultWriteSensitivity() throws Exception {
        testRemoveWithVaultWriteSensitivity(StandardRole.ADMINISTRATOR, true);
    }

    private void testRemoveWithVaultWriteSensitivity(StandardRole role, boolean success) throws Exception {
        try {
            VaultExpressionSensitivityConfig.INSTANCE.setConfiguredRequiresWritePermission(true);

            ChildResourceDefinition def = new ChildResourceDefinition(ONE);
            def.addAttribute("test");
            rootRegistration.registerSubModel(def);

            Resource resourceA = Resource.Factory.create();
            resourceA.getModel().get("test").set("a");
            rootResource.registerChild(ONE_A, resourceA);

            Resource resourceB = Resource.Factory.create();
            resourceB.getModel().get("test").set("${VAULT::AA::bb::cc}");
            rootResource.registerChild(ONE_B, resourceB);

            ModelNode op = Util.createRemoveOperation(ONE_B_ADDR);
            op.get(OPERATION_HEADERS, "roles").set(role.toString());
            if (success) {
                executeForResult(op);
            } else {
                executeForFailure(op);
            }
        } finally {
            VaultExpressionSensitivityConfig.INSTANCE.setConfiguredRequiresAccessPermission(null);
            VaultExpressionSensitivityConfig.INSTANCE.setConfiguredRequiresReadPermission(null);
            VaultExpressionSensitivityConfig.INSTANCE.setConfiguredRequiresWritePermission(null);
        }
    }


    @Override
    protected AbstractControllerTestBase.ModelControllerService createModelControllerService(ProcessType processType) {
        return new AbstractControllerTestBase.ModelControllerService(processType, new RootResourceDefinition());
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        this.rootResource = managementModel.getRootResource();
        this.rootRegistration = managementModel.getRootResourceRegistration();

        GlobalNotifications.registerGlobalNotifications(rootRegistration, processType);
    }

    private static class RootResourceDefinition extends SimpleResourceDefinition {
        RootResourceDefinition() {
            super(new Parameters(PathElement.pathElement("root"), NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(new AbstractAddStepHandler() {})
                    .setRemoveHandler(new AbstractRemoveStepHandler() {}));
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            GlobalOperationHandlers.registerGlobalOperations(resourceRegistration, ProcessType.EMBEDDED_SERVER);
        }
    }

    private static class ChildResourceDefinition extends SimpleResourceDefinition implements ResourceDefinition {
        private final List<AttributeDefinition> attributes = Collections.synchronizedList(new ArrayList<AttributeDefinition>());

        ChildResourceDefinition(PathElement element, AccessConstraintDefinition...constraints){
            super(new Parameters(element, NonResolvingResourceDescriptionResolver.INSTANCE)
                    .setAddHandler(new AbstractAddStepHandler() {})
                    .setRemoveHandler(new AbstractRemoveStepHandler() {})
                    .setAccessConstraints(constraints));
        }

        void addAttribute(String name, AccessConstraintDefinition...constraints) {
            SimpleAttributeDefinitionBuilder builder = new SimpleAttributeDefinitionBuilder(name, ModelType.STRING);
            if (constraints != null) {
                builder.setAccessConstraints(constraints);
            }
            builder.setAllowExpression(true);
            attributes.add(builder.build());
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            for (AttributeDefinition attribute : attributes) {
                resourceRegistration.registerReadWriteAttribute(attribute, null, new ModelOnlyWriteAttributeHandler(attribute));
            }
        }
    }

}
