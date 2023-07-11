/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Records information about capability reference information encoded in an attribute's value.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public interface CapabilityReferenceRecorder extends Feature {

    /**
     * Registers capability requirement information to the given context.
     *
     * @param context the context
     * @param resource the resource on which requirements are gathered
     * @param attributeName the name of the attribute
     * @param attributeValues the values of the attribute, which may contain null
     */
    void addCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues);

    /**
     * Deregisters capability requirement information from the given context.
     *
     * @param context the context
     * @param resource the resource on which requirements are gathered
     * @param attributeName the name of the attribute
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
     * Returns the elements to be added to the baseRequirementName to build the capability name pattern. It will return
     * an array of the form `segment[.segment]` where each segment represents either the name of one of the resource's
     * attributes or one of the keys in the resource's address. In the actual name the attribute name or address key
     * will be replaced by the value associated with that attribute or key.
     *
     * @param name the name of the attribute.
     * @param address the registration address of the resource definition that has the capability and its requirement.
     * @return the elements to be added to the baseRequirementName to build the capability name pattern.
     */
    default String[] getRequirementPatternSegments(String name, PathAddress address) {
        if (name != null && !name.isEmpty()) {
            return new String[]{name};
        }
        return new String[0];
    }

    /**
     * Default implementation of {@link org.jboss.as.controller.CapabilityReferenceRecorder}. Derives the required
     * capability name from the {@code baseRequirementName} provided to the constructor and from the attribute value.
     * Derives the dependent capability name from the {@code baseDependentName} provided to the constructor, and, if the
     * dependent name is dynamic, from the address of the resource currently being processed.
     */
    class DefaultCapabilityReferenceRecorder extends ContextDependencyRecorder {

        private final String baseDependentName;

        DefaultCapabilityReferenceRecorder(String baseRequirementName, String baseDependentName) {
            super(baseRequirementName);
            this.baseDependentName = baseDependentName;
        }

        @Override
        String getDependentName(OperationContext context) {
            RuntimeCapability<?> cap = getDependentCapability(context);
            if (cap != null) {
                // If the cap is registered with the mrr, we ignore any dynamicDependent setting
                return getDependentName(cap, context);
            }
            // No cap registered. Fail!
            throw ControllerLogger.ROOT_LOGGER.unknownCapability(this.baseDependentName);
        }

        @Override
        RuntimeCapability<?> getDependentCapability(OperationContext context) {
            ImmutableManagementResourceRegistration mrr = context.getResourceRegistration();
            for (RuntimeCapability<?> capability : mrr.getCapabilities()) {
                if (this.baseDependentName.equals(capability.getName())) {
                    return capability;
                }
            }
            return null;
        }

        @Override
        public String getBaseDependentName() {
            return this.baseDependentName;
        }
    }

    /**
     * {@link CapabilityReferenceRecorder} that determines the dependent capability from the {@link OperationContext}.
     * This assumes that the
     * {@link OperationContext#getResourceRegistration() resource registration associated with currently executing step}
     * will expose a {@link ImmutableManagementResourceRegistration#getCapabilities() capability set} including one and
     * only one capability. <strong>This recorder cannot be used with attributes associated with resources that do not
     * meet this requirement.</strong>
     */
    class ContextDependencyRecorder implements CapabilityReferenceRecorder {

        private final String baseRequirementName;

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
            String dependentName = getDependentName(context);
            for (String attributeValue : attributeValues) {
                //if (attributeValue != null || !cap.getDynamicOptionalRequirements().contains(baseRequirementName)) { //once we figure out what to do with optional requirements
                if (attributeValue != null) {
                    String requirementName = getRequirementName(context, resource, attributeValue);
                    if (remove) {
                        context.deregisterCapabilityRequirement(requirementName, dependentName, attributeName);
                    } else {
                        context.registerAdditionalCapabilityRequirement(requirementName, dependentName, attributeName);
                    }
                }
            }
        }

        String getDependentName(OperationContext context) {
            RuntimeCapability<?> cap = getDependentCapability(context);
            return getDependentName(cap, context);
        }

        RuntimeCapability<?> getDependentCapability(OperationContext context) {
            ImmutableManagementResourceRegistration mrr = context.getResourceRegistration();
            Set<RuntimeCapability> capabilities = mrr.getCapabilities();
            assert capabilities != null && capabilities.size() == 1;
            return capabilities.iterator().next();
        }

        final String getDependentName(RuntimeCapability<?> cap, OperationContext context) {
            if (cap.isDynamicallyNamed()) {
                return cap.fromBaseCapability(context.getCurrentAddress()).getName();
            }
            return cap.getName();
        }

        protected String getRequirementName(OperationContext context, Resource resource, String attributeValue) {
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
     * {@link CapabilityReferenceRecorder} that determines the dependent capability from the {@link OperationContext}
     * and any additional attributes on same resource. This assumes that the
     * {@link OperationContext#getResourceRegistration() resource registration associated with currently executing step}
     * will expose a {@link ImmutableManagementResourceRegistration#getCapabilities() capability set} including one and
     * only one capability. <strong>This recorder cannot be used with attributes associated with resources that do not
     * meet this requirement.</strong>
     */
    class CompositeAttributeDependencyRecorder extends ContextDependencyRecorder {

        private AttributeDefinition[] attributes;
        private RuntimeCapability capability;

        /**
         * @param baseRequirementName base requirement name
         * @param attributes list of additional attributes on same resource that are used to dynamically construct name
         * of capability
         */
        CompositeAttributeDependencyRecorder(String baseRequirementName, AttributeDefinition... attributes) {
            super(baseRequirementName);
            this.attributes = attributes;
            this.capability = null;
        }

        /**
         * @param capability dependant capability, useful when resource provides more than single capability.
         * @param baseRequirementName base requirement name
         * @param attributes list of additional attributes on same resource that are used to dynamically construct name
         * of capability
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
            return RuntimeCapability.buildDynamicCapabilityName(this.getBaseRequirementName(), dynamicParts);
        }

        @Override
        public String[] getRequirementPatternSegments(String dynamicElement, PathAddress address) {
            List<String> dynamicParts = new ArrayList<>();
            for (int i = 0; i < attributes.length; i++) {
                dynamicParts.add(attributes[i].getName());
            }
            if (dynamicElement != null && !dynamicElement.isEmpty()) {
                String element = dynamicElement;
                dynamicParts.add(element);
            }
            return dynamicParts.toArray(new String[dynamicParts.size()]);
        }
    }

    /**
     * {@link CapabilityReferenceRecorder} that determines the dependent and required capability from the
     * {@link PathAddress} of the resource registration.
     */
    class ResourceCapabilityReferenceRecorder implements CapabilityReferenceRecorder {

        private final Function<PathAddress, String[]> dynamicDependentNameMapper;
        private final Function<PathAddress, String[]> dynamicRequirementNameMapper;
        private final String baseRequirementName;
        private final String baseDependentName;

        public ResourceCapabilityReferenceRecorder(Function<PathAddress, String[]> dynamicDependentNameMapper, String baseDependentName, Function<PathAddress, String[]> dynamicRequirementNameMapper, String baseRequirementName) {
            this.dynamicDependentNameMapper = dynamicDependentNameMapper;
            this.dynamicRequirementNameMapper = dynamicRequirementNameMapper;
            this.baseRequirementName = baseRequirementName;
            this.baseDependentName = baseDependentName;
        }

        public ResourceCapabilityReferenceRecorder(String baseDependentName, Function<PathAddress, String[]> dynamicRequirementNameMapper,String baseRequirementName) {
            this(null, baseDependentName, dynamicRequirementNameMapper, baseRequirementName);
        }

        @Override
        public void addCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues) {
            processCapabilityRequirement(context, attributeName, false);
        }

        @Override
        public void removeCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues) {
            processCapabilityRequirement(context, attributeName, true);
        }

        private void processCapabilityRequirement(OperationContext context, String attributeName, boolean remove) {
            String dependentName = getDependentName(context.getCurrentAddress());
            String requirementName = getRequirementName(context.getCurrentAddress());
            if (remove) {
                context.deregisterCapabilityRequirement(requirementName, dependentName, attributeName);
            } else {
                context.registerAdditionalCapabilityRequirement(requirementName, dependentName, attributeName);
            }
        }

        private String getDependentName(PathAddress address) {
            if (dynamicDependentNameMapper != null) {
                return RuntimeCapability.buildDynamicCapabilityName(baseDependentName, dynamicDependentNameMapper.apply(address));
            }
            return baseDependentName;
        }

        private String getRequirementName(PathAddress address) {
            if (dynamicRequirementNameMapper != null) {
                return RuntimeCapability.buildDynamicCapabilityName(baseRequirementName, dynamicRequirementNameMapper.apply(address));
            }
            return baseRequirementName;
        }

        @Override
        public String getBaseDependentName() {
            return baseDependentName;
        }

        @Override
        public String getBaseRequirementName() {
            return baseRequirementName;
        }

        @Override
        public boolean isDynamicDependent() {
            return dynamicDependentNameMapper != null;
        }

        @Override
        public String[] getRequirementPatternSegments(String dynamicElement, PathAddress registrationAddress) {
            String[] dynamicElements;
            if (registrationAddress != null && dynamicRequirementNameMapper != null) {
                dynamicElements = dynamicRequirementNameMapper.apply(registrationAddress);
            } else {
                dynamicElements = new String[0];
            }
            if (dynamicElement != null && !dynamicElement.isEmpty()) {
                String[] result = new String[dynamicElements.length + 1];
                for (int i = 0; i < dynamicElements.length; i++) {
                    if (dynamicElements[i].charAt(0) == '$') {
                        result[i] = dynamicElements[i].substring(1);
                    } else {
                        result[i] = dynamicElements[i];
                    }
                }
                result[dynamicElements.length] = dynamicElement;
                return result;
            }
            return dynamicElements;
        }
    }

}
