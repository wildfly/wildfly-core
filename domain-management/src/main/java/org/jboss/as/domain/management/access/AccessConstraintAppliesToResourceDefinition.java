/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintUtilization;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@code ResourceDefinition} for the resources that expose what resources an
 * {@link org.jboss.as.controller.access.management.AccessConstraintDefinition} applies to.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class AccessConstraintAppliesToResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(ModelDescriptionConstants.APPLIES_TO);

    public static final AttributeDefinition ADDRESS =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ADDRESS, ModelType.STRING).build();

    public static final AttributeDefinition ENTIRE_RESOURCE =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ENTIRE_RESOURCE, ModelType.BOOLEAN)
            .build();

    public static final AttributeDefinition ATTRIBUTES =
            new SimpleListAttributeDefinition.Builder(ModelDescriptionConstants.ATTRIBUTES,
                    new SimpleAttributeDefinitionBuilder("attribute", ModelType.STRING).build())
                    .build();

    public static final AttributeDefinition OPERATIONS =
            new SimpleListAttributeDefinition.Builder(ModelDescriptionConstants.OPERATIONS,
                    new SimpleAttributeDefinitionBuilder("operation", ModelType.STRING).build())
                    .build();

    public AccessConstraintAppliesToResourceDefinition() {
        super(new Parameters(PATH_ELEMENT, DomainManagementResolver.getResolver("core.access-control.constraint.applies-to")).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(ADDRESS, ReadResourceNameOperationStepHandler.INSTANCE);
        resourceRegistration.registerReadOnlyAttribute(ENTIRE_RESOURCE, new EntireResourceHandler());
        resourceRegistration.registerReadOnlyAttribute(ATTRIBUTES, new AttributesHandler());
        resourceRegistration.registerReadOnlyAttribute(OPERATIONS, new OperationsHandler());
    }

    static Resource.ResourceEntry createResource(AccessConstraintUtilization constraintUtilization) {
        return new AccessConstraintAppliesToResource(constraintUtilization);
    }

    private static class AccessConstraintAppliesToResource extends PlaceholderResource.PlaceholderResourceEntry {

        private final AccessConstraintUtilization constraintUtilization;

        private AccessConstraintAppliesToResource(AccessConstraintUtilization constraintUtilization) {
            super(PathElement.pathElement(ModelDescriptionConstants.APPLIES_TO,
                    constraintUtilization.getPathAddress().toCLIStyleString()));
            this.constraintUtilization = constraintUtilization;
        }
    }

    private abstract static class AccessConstraintAppliesToHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            AccessConstraintAppliesToResource resource =
                    (AccessConstraintAppliesToResource) context.readResource(PathAddress.EMPTY_ADDRESS);
            setResult(context, resource.constraintUtilization);
        }

        abstract void setResult(OperationContext context, AccessConstraintUtilization constraintUtilization);
    }

    private static class EntireResourceHandler extends AccessConstraintAppliesToHandler {

        @Override
        void setResult(OperationContext context, AccessConstraintUtilization constraintUtilization) {
            context.getResult().set(constraintUtilization.isEntireResourceConstrained());
        }
    }

    private abstract static class StringSetHandler extends AccessConstraintAppliesToHandler {

        @Override
        void setResult(OperationContext context, AccessConstraintUtilization constraintUtilization) {
            ModelNode result = context.getResult();
            result.setEmptyList();
            for (String attribute : getStringSet(constraintUtilization)) {
                result.add(attribute);
            }
        }

        abstract Set<String> getStringSet(AccessConstraintUtilization constraintUtilization);
    }

    private static class AttributesHandler extends StringSetHandler {

        @Override
        Set<String> getStringSet(AccessConstraintUtilization constraintUtilization) {
            if (constraintUtilization.isEntireResourceConstrained()) {
                // Showing individual attributes is redundant and confusing
                return Collections.emptySet();
            }
            return constraintUtilization.getAttributes();
        }
    }

    private static class OperationsHandler extends StringSetHandler {

        @Override
        Set<String> getStringSet(AccessConstraintUtilization constraintUtilization) {
            if (constraintUtilization.isEntireResourceConstrained()) {
                // Showing individual operations is redundant and confusing
                return Collections.emptySet();
            }
            return constraintUtilization.getOperations();
        }
    }
}
