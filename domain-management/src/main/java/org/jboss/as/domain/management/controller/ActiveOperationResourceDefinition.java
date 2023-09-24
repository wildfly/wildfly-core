/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelType;


/**
 * {@code ResourceDefinition} for a currently executing operation.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ActiveOperationResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(ACTIVE_OPERATION);

    static final ResourceDefinition INSTANCE = new ActiveOperationResourceDefinition();

    static final AttributeDefinition OPERATION_NAME =
            SimpleAttributeDefinitionBuilder.create(OP, ModelType.STRING).build();
    static final AttributeDefinition ADDRESS =
            new SimpleMapAttributeDefinition.Builder(OP_ADDR, ModelType.STRING, false).build();
    private static final AttributeDefinition CALLER_THREAD =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.CALLER_THREAD, ModelType.STRING).build();
    private static final AttributeDefinition ACCESS_MECHANISM =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ACCESS_MECHANISM, ModelType.STRING)
                    .setRequired(false)
                    .setValidator(EnumValidator.create(AccessMechanism.class))
                    .build();
    private static final AttributeDefinition DOMAIN_UUID =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.DOMAIN_UUID, ModelType.STRING, true).build();
    private static final AttributeDefinition DOMAIN_ROLLOUT =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.DOMAIN_ROLLOUT, ModelType.BOOLEAN).build();

    private static final AttributeDefinition EXECUTION_STATUS =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.EXECUTION_STATUS, ModelType.STRING)
                    .setValidator(EnumValidator.create(OperationContext.ExecutionStatus.class))
                    .build();
    private static final AttributeDefinition CANCELLED =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.CANCELLED, ModelType.BOOLEAN).build();
    private static final AttributeDefinition RUNNING_TIME =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.RUNNING_TIME, ModelType.LONG)
                    .setMeasurementUnit(MeasurementUnit.NANOSECONDS)
                    .build();
    private static final AttributeDefinition EXCLUSIVE_RUNNING_TIME =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.EXCLUSIVE_RUNNING_TIME, ModelType.LONG)
                    .setMeasurementUnit(MeasurementUnit.NANOSECONDS)
                    .build();

    private ActiveOperationResourceDefinition() {
        super(new Parameters(PATH_ELEMENT, DomainManagementResolver.getResolver(CORE, MANAGEMENT_OPERATIONS, ACTIVE_OPERATION)).setRuntime());
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        resourceRegistration.registerOperationHandler(CancelActiveOperationHandler.DEFINITION, CancelActiveOperationHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadOnlyAttribute(OPERATION_NAME, SecureOperationReadHandler.INSTANCE);
        resourceRegistration.registerReadOnlyAttribute(ADDRESS, SecureOperationReadHandler.INSTANCE);
        resourceRegistration.registerReadOnlyAttribute(CALLER_THREAD, null);
        resourceRegistration.registerReadOnlyAttribute(ACCESS_MECHANISM, null);
        resourceRegistration.registerReadOnlyAttribute(DOMAIN_UUID, null);
        resourceRegistration.registerReadOnlyAttribute(DOMAIN_ROLLOUT, null);
        resourceRegistration.registerReadOnlyAttribute(EXECUTION_STATUS, null);
        resourceRegistration.registerReadOnlyAttribute(RUNNING_TIME, null);
        resourceRegistration.registerReadOnlyAttribute(EXCLUSIVE_RUNNING_TIME, null);
        resourceRegistration.registerReadOnlyAttribute(CANCELLED, null);
    }
}
