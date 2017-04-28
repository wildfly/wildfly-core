/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import java.util.Set;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Records information about capability reference information encoded in an attribute's value.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public interface CapabilityReferenceRecorder {

    /**
     * Registers capability requirement information to the given context.
     * @param context         the context
     * @param resource        the resource on which requirements are gathered
     * @param attributeName   the name of the attribute
     * @param attributeValues the values of the attribute, which may contain null
     */
    void addCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues);

    /**
     * Deregisters capability requirement information from the given context.
     * @param context         the context
     * @param resource        the resource on which requirements are gathered
     * @param attributeName   the name of the attribute
     * @param attributeValues the values of the attribute, which may contain null
     */
    void removeCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues);

    /**
     * @return base name of dependant, usually name of the attribute that provides reference to capability
     * @deprecated No longer required and may throw {@link java.lang.UnsupportedOperationException}
     */
    @Deprecated
    String getBaseDependentName();

    /**
     * @return requirement name of the capability this reference depends on
     */
    String getBaseRequirementName();

    /**
     * @return tells is reference is dynamic or static, in case where it is dynamic it uses base name + name of
     * dependent attribute to construct name of capability
     * @deprecated No longer required and may throw {@link java.lang.UnsupportedOperationException}
     */
    @Deprecated
    default boolean isDynamicDependent() {
        throw new UnsupportedOperationException();
    }

    /**
     * Default implementation of {@link org.jboss.as.controller.CapabilityReferenceRecorder}.
     * Derives the required capability name from the {@code baseRequirementName} provided to the constructor and from
     * the attribute value. Derives the dependent capability name from the {@code baseDependentName} provided to the
     * constructor, and, if the dependent name is dynamic, from the address of the resource currently being processed.
     */
    class DefaultCapabilityReferenceRecorder implements CapabilityReferenceRecorder {

        private final String baseRequirementName;
        private final String baseDependentName;
        private final boolean dynamicDependent;

        DefaultCapabilityReferenceRecorder(String baseRequirementName, String baseDependentName, boolean dynamicDependent) {
            this.baseRequirementName = baseRequirementName;
            this.baseDependentName = baseDependentName;
            this.dynamicDependent = dynamicDependent;
        }

        @Override
        public final void addCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues) {
            processCapabilityRequirement(context, attributeName, false, attributeValues);
        }

        @Override
        public final void removeCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues) {
            processCapabilityRequirement(context, attributeName, true, attributeValues);
        }

        private void processCapabilityRequirement(OperationContext context, String attributeName, boolean remove, String... attributeValues) {
            String dependentName;
            if (dynamicDependent) {
                dependentName = RuntimeCapability.buildDynamicCapabilityName(baseDependentName, getDynamicDependentName(context.getCurrentAddress()));
            } else {
                dependentName = baseDependentName;
            }

            for (String attributeValue : attributeValues) {
                // This implementation does not handle null attribute values
                if (attributeValue != null) {
                    String requirementName = RuntimeCapability.buildDynamicCapabilityName(baseRequirementName, attributeValue);
                    if (remove) {
                        context.deregisterCapabilityRequirement(requirementName, dependentName);
                    } else {
                        context.registerAdditionalCapabilityRequirement(requirementName, dependentName, attributeName);
                    }
                }
            }
        }

        /**
         * Determines the dynamic portion of the dependent capability's name. Only invoked if {@code dynamicDependent}
         * is set to {@code true} in the constructor.
         * <p>
         * This base implementation returns the value of the last element in {@code currentAddress}. Subclasses that
         * wish to extract the relevant name from some other element in the address may override this.
         * </p>
         *
         * @param currentAddress the address of the resource currently being processed. Will not be {@code null}
         * @return the dynamic portion of the dependenty capability name. Cannot be {@code null}
         */
        String getDynamicDependentName(PathAddress currentAddress) {
            return currentAddress.getLastElement().getValue();
        }

        @Override
        public String getBaseDependentName() {
            return baseDependentName;
        }

        @Override
        public String getBaseRequirementName() {
            return baseRequirementName;
        }

    }

    /**
     * {@link CapabilityReferenceRecorder} that determines the dependent capability from
     * the {@link OperationContext}. This assumes that the
     * {@link OperationContext#getResourceRegistration() resource registration associated with currently executing step}
     * will expose a {@link ImmutableManagementResourceRegistration#getCapabilities() capability set} including
     * one and only one capability. <strong>This recorder cannot be used with attributes associated with resources
     * that do not meet this requirement.</strong>
     */
    class ContextDependencyRecorder implements CapabilityReferenceRecorder {

        final String baseRequirementName;

        ContextDependencyRecorder(String baseRequirementName) {
            this.baseRequirementName = baseRequirementName;
        }

        /**
         * {@inheritDoc}
         *
         * @throws AssertionError if the requirements discussed in the class javadoc are not fulfilled
         */
        @Override
        public final void addCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues) {
            processCapabilityRequirement(context, resource, attributeName, false, attributeValues);
        }

        /**
         * {@inheritDoc}
         *
         * @throws AssertionError if the requirements discussed in the class javadoc are not fulfilled
         */
        @Override
        public final void removeCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues) {
            processCapabilityRequirement(context, resource, attributeName, true, attributeValues);
        }

        void processCapabilityRequirement(OperationContext context, Resource resource, String attributeName, boolean remove, String... attributeValues) {
            RuntimeCapability cap = getDependentCapability(context);
            String dependentName = getDependentName(cap, context);
            for (String attributeValue : attributeValues) {
                //if (attributeValue != null || !cap.getDynamicOptionalRequirements().contains(baseRequirementName)) { //once we figure out what to do with optional requirements
                if (attributeValue != null) {
                    String requirementName = getRequirementName(context, resource, attributeValue);
                    if (remove) {
                        context.deregisterCapabilityRequirement(requirementName, dependentName);
                    } else {
                        context.registerAdditionalCapabilityRequirement(requirementName, dependentName, attributeName);
                    }
                }
            }
        }

        protected RuntimeCapability getDependentCapability(OperationContext context) {
            ImmutableManagementResourceRegistration mrr = context.getResourceRegistration();
            Set<RuntimeCapability> capabilities = mrr.getCapabilities();
            assert capabilities != null && capabilities.size() == 1;
            return capabilities.iterator().next();
        }

        String getDependentName(RuntimeCapability cap, OperationContext context) {
            if (cap.isDynamicallyNamed()) {
                return cap.fromBaseCapability(context.getCurrentAddress()).getName();
            } else {
                return cap.getName();
            }
        }
        protected String getRequirementName(OperationContext context, Resource resource, String attributeValue){
            return RuntimeCapability.buildDynamicCapabilityName(baseRequirementName, attributeValue);
        }

        /**
         * Throws {@link UnsupportedOperationException}
         */
        @Override
        public String getBaseDependentName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getBaseRequirementName() {
            return baseRequirementName;
        }
    }

    /**
     * {@link CapabilityReferenceRecorder} that determines the dependent capability from
     * the {@link OperationContext} and any additional attributes on same resource.
     * This assumes that the {@link OperationContext#getResourceRegistration() resource registration associated with currently executing step}
     * will expose a {@link ImmutableManagementResourceRegistration#getCapabilities() capability set} including
     * one and only one capability. <strong>This recorder cannot be used with attributes associated with resources
     * that do not meet this requirement.</strong>
     */
    class CompositeAttributeDependencyRecorder extends ContextDependencyRecorder {

        private AttributeDefinition[] attributes;
        private RuntimeCapability capability;

        /**
         * @param baseRequirementName base requirement name
         * @param attributes list of additional attributes on same resource that are used to dynamically construct name of capability
         */
        CompositeAttributeDependencyRecorder(String baseRequirementName, AttributeDefinition... attributes) {
            super(baseRequirementName);
            this.attributes = attributes;
            this.capability = null;
        }

        /**
         * @param capability dependant capability, useful when resource provides more than single capability.
         * @param baseRequirementName base requirement name
         * @param attributes          list of additional attributes on same resource that are used to dynamically construct name of capability
         */
        CompositeAttributeDependencyRecorder(RuntimeCapability capability, String baseRequirementName, AttributeDefinition... attributes) {
            super(baseRequirementName);
            this.attributes = attributes;
            this.capability = capability;
        }

        @Override
        protected RuntimeCapability getDependentCapability(OperationContext context) {
            if (capability != null) {
                return capability;
            }
            return super.getDependentCapability(context);
        }

        @Override
        protected String getRequirementName(OperationContext context, Resource resource, String attributeValue) {
            ModelNode model = resource.getModel();
            String[] dynamicParts = new String[attributes.length + 1];
            try {
                for (int i = 0; i < attributes.length; i++) {
                    AttributeDefinition ad = attributes[i];
                    dynamicParts[i] = ad.resolveModelAttribute(context, model).asString();
                }
            } catch (OperationFailedException e) {
                throw new RuntimeException(e);
            }
            dynamicParts[attributes.length] = attributeValue;
            return RuntimeCapability.buildDynamicCapabilityName(baseRequirementName, dynamicParts);
        }

    }

}
