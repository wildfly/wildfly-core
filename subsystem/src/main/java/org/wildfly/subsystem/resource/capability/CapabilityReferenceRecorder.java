/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.ServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A {@link org.jboss.as.controller.CapabilityReferenceRecorder} whose requirement is specified as a {@link ServiceDescriptor}.
 * @author Paul Ferraro
 */
public interface CapabilityReferenceRecorder<T> extends org.jboss.as.controller.CapabilityReferenceRecorder {

    /**
     * Returns the dependent capability.
     * @return a capability
     */
    RuntimeCapability<Void> getDependent();

    /**
     * Returns the service descriptor required by the dependent capability.
     * @return a service descriptor
     */
    ServiceDescriptor<T> getRequirement();

    @Override
    default String getBaseRequirementName() {
        return this.getRequirement().getName();
    }

    @Override
    default String getBaseDependentName() {
        return this.getDependent().getName();
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> Builder<T> builder(RuntimeCapability<Void> capability, UnaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, requirement, List.of());
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's parent segment derives from the path of the current resource.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> BinaryBuilder<T> builder(RuntimeCapability<Void> capability, BinaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, requirement, List.of(DefaultBuilder.CHILD_PATH));
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's grandparent and parent segments derive from the path of the parent and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> TernaryBuilder<T> builder(RuntimeCapability<Void> capability, TernaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, requirement, List.of(DefaultBuilder.PARENT_PATH, DefaultBuilder.CHILD_PATH));
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's great-grandparent, grandparent, and parent segments derive from the path of the grandparent, parent, and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> QuaternaryBuilder<T> builder(RuntimeCapability<Void> capability, QuaternaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, requirement, List.of(DefaultBuilder.GRANDPARENT_PATH, DefaultBuilder.PARENT_PATH, DefaultBuilder.CHILD_PATH));
    }

    interface Builder<T> {
        /**
         * Builds a capability reference recorder.
         * @return a capability reference recorder
         */
        CapabilityReferenceRecorder<T> build();
    }

    interface ParentAttributeProvider<T> {
        /**
         * Specifies the attribute used to resolves the parent segment of the requirement.
         * @param attribute the attribute used to resolve the parent segment of the requirement.
         * @return a reference to this builder
         */
        Builder<T> withParentAttribute(AttributeDefinition attribute);
    }

    interface BinaryBuilder<T> extends ParentAttributeProvider<T>, Builder<T> {
        /**
         * Overrides the default path resolver for the parent segment of the requirement.
         * @param a path resolver
         * @return a reference to this builder
         */
        Builder<T> withParentPathResolver(Function<PathAddress, PathElement> resolver);
    }

    interface GrandparentAttributeProvider<T> {
        /**
         * Specifies the attribute used to resolves the grandparent segment of the requirement.
         * @param attribute the attribute used to resolve the parent segment of the requirement.
         * @return a reference to this builder
         */
        ParentAttributeProvider<T> withGrandparentAttribute(AttributeDefinition attribute);
    }

    interface TernaryBuilder<T> extends BinaryBuilder<T>, GrandparentAttributeProvider<T> {
        /**
         * Overrides the default path resolver for the grandparent segment of the requirement.
         * @param a path resolver
         * @return a reference to this builder
         */
        BinaryBuilder<T> withGrandparentPathResolver(Function<PathAddress, PathElement> resolver);
    }

    interface GreatGrandparentAttributeProvider<T> {
        /**
         * Specifies the attribute used to resolves the great-grandparent segment of the requirement.
         * @param attribute the attribute used to resolve the parent segment of the requirement.
         * @return a reference to this builder
         */
        GrandparentAttributeProvider<T> withGreatGrandparentAttribute(AttributeDefinition attribute);
    }

    interface QuaternaryBuilder<T> extends TernaryBuilder<T>, GreatGrandparentAttributeProvider<T> {
        /**
         * Overrides the default path resolver for the great-grandparent segment of the requirement.
         * @param a path resolver
         * @return a reference to this builder
         */
        TernaryBuilder<T> withGreatGrandparentPathResolver(Function<PathAddress, PathElement> resolver);
    }

    class DefaultBuilder<T> implements QuaternaryBuilder<T> {
        private static final Function<PathAddress, PathElement> CHILD_PATH = PathAddress::getLastElement;
        private static final Function<PathAddress, PathElement> PARENT_PATH = CHILD_PATH.compose(PathAddress::getParent);
        private static final Function<PathAddress, PathElement> GRANDPARENT_PATH = PARENT_PATH.compose(PathAddress::getParent);
        private static final BiFunction<OperationContext, String, String> CHILD_REQUIREMENT_NAME_SEGMENT_RESOLVER = (context, value) -> value;
        private static final BiFunction<PathAddress, String, String> CHILD_REQUIREMENT_PATTERN_SEGMENT_RESOLVER = (address, name) -> name;

        private final RuntimeCapability<Void> capability;
        private final ServiceDescriptor<T> requirement;
        private final List<BiFunction<OperationContext, String, String>> requirementNameSegmentResolvers;
        private final List<BiFunction<PathAddress, String, String>> requirementPatternSegmentResolvers;
        private final int greatGrandparentIndex;
        private final int grandparentIndex;
        private final int parentIndex;

        DefaultBuilder(RuntimeCapability<Void> capability, ServiceDescriptor<T> requirement, List<Function<PathAddress, PathElement>> defaultResolvers) {
            this.capability = capability;
            this.requirement = requirement;
            this.parentIndex = defaultResolvers.size() - 1;
            this.grandparentIndex = this.parentIndex - 1;
            this.greatGrandparentIndex = this.grandparentIndex - 1;
            this.requirementNameSegmentResolvers = new ArrayList<>(defaultResolvers.size() + 1);
            this.requirementPatternSegmentResolvers = new ArrayList<>(defaultResolvers.size() + 1);
            for (Function<PathAddress, PathElement> resolver : defaultResolvers) {
                this.requirementNameSegmentResolvers.add(createRequirementNameSegmentResolver(resolver));
                this.requirementPatternSegmentResolvers.add(createRequirementPatternSegmentResolver(resolver));
            }
            this.requirementNameSegmentResolvers.add(CHILD_REQUIREMENT_NAME_SEGMENT_RESOLVER);
            this.requirementPatternSegmentResolvers.add(CHILD_REQUIREMENT_PATTERN_SEGMENT_RESOLVER);
        }

        @Override
        public GrandparentAttributeProvider<T> withGreatGrandparentAttribute(AttributeDefinition attribute) {
            this.setAttribute(this.greatGrandparentIndex, attribute);
            return this;
        }

        @Override
        public ParentAttributeProvider<T> withGrandparentAttribute(AttributeDefinition attribute) {
            this.setAttribute(this.grandparentIndex, attribute);
            return this;
        }

        @Override
        public Builder<T> withParentAttribute(AttributeDefinition attribute) {
            this.setAttribute(this.parentIndex, attribute);
            return this;
        }

        @Override
        public TernaryBuilder<T> withGreatGrandparentPathResolver(Function<PathAddress, PathElement> resolver) {
            this.setPathResolver(this.greatGrandparentIndex, resolver);
            return this;
        }

        @Override
        public BinaryBuilder<T> withGrandparentPathResolver(Function<PathAddress, PathElement> resolver) {
            this.setPathResolver(this.grandparentIndex, resolver);
            return this;
        }

        @Override
        public Builder<T> withParentPathResolver(Function<PathAddress, PathElement> resolver) {
            this.setPathResolver(this.parentIndex, resolver);
            return this;
        }

        private void setAttribute(int index, AttributeDefinition attribute) {
            this.requirementNameSegmentResolvers.set(index, createRequirementNameSegmentResolver(attribute));
            this.requirementPatternSegmentResolvers.set(index, createRequirementPatternSegmentResolver(attribute));
        }

        private void setPathResolver(int index, Function<PathAddress, PathElement> resolver) {
            this.requirementNameSegmentResolvers.set(index, createRequirementNameSegmentResolver(resolver));
            this.requirementPatternSegmentResolvers.set(index, createRequirementPatternSegmentResolver(resolver));
        }

        private static BiFunction<OperationContext, String, String> createRequirementNameSegmentResolver(AttributeDefinition attribute) {
            return new BiFunction<>() {
                @Override
                public String apply(OperationContext context, String value) {
                    ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel();
                    return model.get(attribute.getName()).asString();
                }
            };
        }

        private static BiFunction<OperationContext, String, String> createRequirementNameSegmentResolver(Function<PathAddress, PathElement> pathResolver) {
            return new BiFunction<>() {
                @Override
                public String apply(OperationContext context, String value) {
                    return pathResolver.apply(context.getCurrentAddress()).getValue();
                }
            };
        }

        private static BiFunction<PathAddress, String, String> createRequirementPatternSegmentResolver(AttributeDefinition attribute) {
            return new BiFunction<>() {
                @Override
                public String apply(PathAddress address, String name) {
                    return attribute.getName();
                }
            };
        }

        private static BiFunction<PathAddress, String, String> createRequirementPatternSegmentResolver(Function<PathAddress, PathElement> pathResolver) {
            return new BiFunction<>() {
                @Override
                public String apply(PathAddress address, String name) {
                    return pathResolver.apply(address).getKey();
                }
            };
        }

        @Override
        public CapabilityReferenceRecorder<T> build() {
            return new CapabilityServiceDescriptorReferenceRecorder<>(this.capability, this.requirement, this.requirementNameSegmentResolvers, this.requirementPatternSegmentResolvers);
        }
    }

    abstract class AbstractCapabilityServiceDescriptorReferenceRecorder<T> implements CapabilityReferenceRecorder<T> {

        private final RuntimeCapability<Void> capability;
        private final ServiceDescriptor<T> requirement;

        AbstractCapabilityServiceDescriptorReferenceRecorder(RuntimeCapability<Void> capability, ServiceDescriptor<T> requirement) {
            this.capability = capability;
            this.requirement = requirement;
        }

        @Override
        public RuntimeCapability<Void> getDependent() {
            return this.capability;
        }

        @Override
        public ServiceDescriptor<T> getRequirement() {
            return this.requirement;
        }

        String resolveDependentName(OperationContext context) {
            return this.getDependent().fromBaseCapability(context.getCurrentAddress()).getName();
        }

        @Override
        public int hashCode() {
            return this.capability.getName().hashCode();
        }

        @Override
        public boolean equals(Object object) {
            return (object instanceof CapabilityReferenceRecorder) ? this.getDependent().equals(((CapabilityReferenceRecorder<?>) object).getDependent()) : false;
        }
    }

    class CapabilityServiceDescriptorReferenceRecorder<T> extends AbstractCapabilityServiceDescriptorReferenceRecorder<T> {

        private final List<BiFunction<OperationContext, String, String>> requirementNameSegmentResolvers;
        private final List<BiFunction<PathAddress, String, String>> requirementPatternSegmentResolvers;

        CapabilityServiceDescriptorReferenceRecorder(RuntimeCapability<Void> capability, ServiceDescriptor<T> requirement, List<BiFunction<OperationContext, String, String>> requirementNameSegmentResolvers, List<BiFunction<PathAddress, String, String>> requirementPatternSegmentResolvers) {
            super(capability, requirement);
            this.requirementNameSegmentResolvers = List.copyOf(requirementNameSegmentResolvers);
            this.requirementPatternSegmentResolvers = List.copyOf(requirementPatternSegmentResolvers);
        }

        @Override
        public void addCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... values) {
            String dependentName = this.resolveDependentName(context);
            for (String value : values) {
                // Do not register requirement if undefined
                if (value != null) {
                    context.registerAdditionalCapabilityRequirement(this.resolveRequirementName(context, value), dependentName, attributeName);
                }
            }
        }

        @Override
        public void removeCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... values) {
            String dependentName = this.resolveDependentName(context);
            for (String value : values) {
                // We did not register a requirement if undefined
                if (value != null) {
                    context.deregisterCapabilityRequirement(this.resolveRequirementName(context, value), dependentName);
                }
            }
        }

        private String resolveRequirementName(OperationContext context, String value) {
            String[] segments = new String[this.requirementNameSegmentResolvers.size()];
            ListIterator<BiFunction<OperationContext, String, String>> resolvers = this.requirementNameSegmentResolvers.listIterator();
            while (resolvers.hasNext()) {
                segments[resolvers.nextIndex()] = resolvers.next().apply(context, value);
            }
            return RuntimeCapability.buildDynamicCapabilityName(this.getBaseRequirementName(), segments);
        }

        @Override
        public String[] getRequirementPatternSegments(String name, PathAddress address) {
            String[] segments = new String[this.requirementPatternSegmentResolvers.size()];
            ListIterator<BiFunction<PathAddress, String, String>> resolvers = this.requirementPatternSegmentResolvers.listIterator();
            while (resolvers.hasNext()) {
                segments[resolvers.nextIndex()] = resolvers.next().apply(address, name);
            }
            return segments;
        }
    }
}
