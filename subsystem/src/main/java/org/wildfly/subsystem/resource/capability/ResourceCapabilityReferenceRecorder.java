/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.BinaryCapabilityNameResolver;
import org.jboss.as.controller.capability.QuaternaryCapabilityNameResolver;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.TernaryCapabilityNameResolver;
import org.jboss.as.controller.capability.UnaryCapabilityNameResolver;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * A {@link CapabilityReference} specialization that records requirements of a resource, rather than an attribute.
 * @param <T> the requirement type
 * @deprecated Replaced by {@link ResourceCapabilityReference}.
 */
@Deprecated(forRemoval = true, since = "26.0.0")
public interface ResourceCapabilityReferenceRecorder<T> extends ResourceCapabilityReference<T> {

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link ResourceCapabilityReference#builder(RuntimeCapability, NullaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> Builder<T> builder(RuntimeCapability<Void> capability, NullaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement), ResourceCapabilityReference.ResourceCapabilityServiceDescriptorReference.EMPTY_RESOLVER);
    }

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * By default, the requirement name will resolve against the path of the current resource.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link ResourceCapabilityReference#builder(RuntimeCapability, UnaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> NaryBuilder<T> builder(RuntimeCapability<Void> capability, UnaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement), UnaryCapabilityNameResolver.DEFAULT);
    }

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * By default, the requirement name will resolve against the paths of the parent and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link ResourceCapabilityReference#builder(RuntimeCapability, BinaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> NaryBuilder<T> builder(RuntimeCapability<Void> capability, BinaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement), BinaryCapabilityNameResolver.PARENT_CHILD);
    }

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * By default, the requirement name will resolve against the paths of the grandparent, parent, and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link ResourceCapabilityReference#builder(RuntimeCapability, NullaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> NaryBuilder<T> builder(RuntimeCapability<Void> capability, TernaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement), TernaryCapabilityNameResolver.GRANDPARENT_PARENT_CHILD);
    }

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * By default, the requirement name will resolve against the paths of the great-grandparent, grandparent, parent, and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link ResourceCapabilityReference#builder(RuntimeCapability, QuaternaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> NaryBuilder<T> builder(RuntimeCapability<Void> capability, QuaternaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement), QuaternaryCapabilityNameResolver.GREATGRANDPARENT_GRANDPARENT_PARENT_CHILD);
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface Builder<T> extends ResourceCapabilityReference.Builder<T> {
        /**
         * Only reference the provided capability if value of the specified attribute complies with the specified predicate.
         * @param attribute an attribute of the resource to use for conditional registration
         * @param predicate conditionally determines whether to require this capability, depending on the resolve value of the specified attribute
         * @return a reference to this builder
         */
        @Override
        Builder<T> when(AttributeDefinition attribute, Predicate<ModelNode> predicate);

        /**
         * Builds the configured capability reference recorder.
         * @return a capability reference recorder
         */
        @Override
        org.wildfly.subsystem.resource.capability.ResourceCapabilityReferenceRecorder<T> build();
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface NaryBuilder<T> extends ResourceCapabilityReference.NaryBuilder<T>, Builder<T> {
        /**
         * Overrides the default requirement name resolver.
         * @param requirementNameResolver a capability name resolver
         * @return a reference to this builder
         */
        @Override
        Builder<T> withRequirementNameResolver(Function<PathAddress, String[]> requirementNameResolver);
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    static class DefaultBuilder<T> extends ResourceCapabilityReference.DefaultBuilder<T> implements NaryBuilder<T> {

        DefaultBuilder(RuntimeCapability<Void> capability, NaryServiceDescriptor<T> requirement, Function<PathAddress, String[]> defaultRequirementNameResolver) {
            super(capability, requirement, defaultRequirementNameResolver);
        }

        @Override
        public Builder<T> withRequirementNameResolver(Function<PathAddress, String[]> requirementNameResolver) {
            super.withRequirementNameResolver(requirementNameResolver);
            return this;
        }

        @Override
        public Builder<T> when(AttributeDefinition attribute, Predicate<ModelNode> predicate) {
            super.when(attribute, predicate);
            return this;
        }

        @Override
        public org.wildfly.subsystem.resource.capability.ResourceCapabilityReferenceRecorder<T> build() {
            return new ResourceCapabilityServiceDescriptorReference<>(super.build());
        }
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    class ResourceCapabilityServiceDescriptorReference<T> extends CapabilityReferenceRecorder.CapabilityServiceDescriptorReferenceRecorder<T> implements org.wildfly.subsystem.resource.capability.ResourceCapabilityReferenceRecorder<T> {
        private final ResourceCapabilityReference<T> reference;

        ResourceCapabilityServiceDescriptorReference(ResourceCapabilityReference<T> reference) {
            super(reference);
            this.reference = reference;
        }

        @Override
        public ServiceDependency<T> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
            return this.reference.resolve(context, model);
        }

        @Override
        public Function<PathAddress, String[]> getRequirementNameResolver() {
            return this.reference.getRequirementNameResolver();
        }

        @Override
        public void addCapabilityRequirements(OperationContext context, Resource resource) {
            this.reference.addCapabilityRequirements(context, resource);
        }

        @Override
        public void removeCapabilityRequirements(OperationContext context, Resource resource) {
            this.reference.removeCapabilityRequirements(context, resource);
        }
    }
}
