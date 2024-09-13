/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.Map;
import java.util.function.Function;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.ServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A {@link org.jboss.as.controller.CapabilityReferenceRecorder} whose requirement is specified as a {@link org.wildfly.service.descriptor.ServiceDescriptor}.
 * @param <T> the requirement type
 * @deprecated Replaced by {@link CapabilityReference}.
 */
@Deprecated(forRemoval = true, since = "26.0.0")
public interface CapabilityReferenceRecorder<T> extends CapabilityReference<T> {

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link CapabilityReference#builder(RuntimeCapability, UnaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> Builder<T> builder(RuntimeCapability<Void> capability, UnaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement));
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's parent segment derives from the path of the current resource.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link CapabilityReference#builder(RuntimeCapability, BinaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> ParentPathProvider<T> builder(RuntimeCapability<Void> capability, BinaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement));
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's grandparent and parent segments derive from the path of the parent and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link CapabilityReference#builder(RuntimeCapability, TernaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> GrandparentPathProvider<T> builder(RuntimeCapability<Void> capability, TernaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement));
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's great-grandparent, grandparent, and parent segments derive from the path of the grandparent, parent, and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @deprecated Superseded by {@link CapabilityReference#builder(RuntimeCapability, QuaternaryServiceDescriptor)}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    static <T> GreatGrandparentPathProvider<T> builder(RuntimeCapability<Void> capability, QuaternaryServiceDescriptor<T> requirement) {
        return new DefaultBuilder<>(capability, NaryServiceDescriptor.of(requirement));
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface Builder<T> extends CapabilityReference.Builder<T> {
        /**
         * Builds a capability reference recorder.
         * @return a capability reference recorder
         */
        @Override
        CapabilityReferenceRecorder<T> build();
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface ParentAttributeProvider<T> extends CapabilityReference.ParentAttributeProvider<T> {
        /**
         * Specifies the attribute used to resolves the parent segment of the requirement.
         * @param attribute the attribute used to resolve the parent segment of the requirement.
         * @return a reference to this builder
         */
        @Override
        Builder<T> withParentAttribute(AttributeDefinition attribute);
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface ParentPathProvider<T> extends CapabilityReference.ParentPathProvider<T>, ParentAttributeProvider<T> {
        /**
         * Specifies the path for the parent segment of the requirement.
         * @param path a path element used to construct the capability name pattern for this reference
         * @param resolver a path resolver
         * @return a reference to this builder
         */
        @Override
        default Builder<T> withParentPath(PathElement path) {
            return this.withParentPath(path, CapabilityReference.DefaultBuilder.CHILD_PATH);
        }

        /**
         * Specifies the path and resolver for the parent segment of the requirement.
         * @param path a path element used to construct the capability name pattern for this reference
         * @param resolver a path resolver
         * @return a reference to this builder
         */
        @Override
        Builder<T> withParentPath(PathElement path, Function<PathAddress, PathElement> resolver);
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface GrandparentAttributeProvider<T> extends CapabilityReference.GrandparentAttributeProvider<T>{
        /**
         * Specifies the attribute used to resolves the grandparent segment of the requirement.
         * @param attribute the attribute used to resolve the parent segment of the requirement.
         * @return a reference to this builder
         */
        @Override
        ParentAttributeProvider<T> withGrandparentAttribute(AttributeDefinition attribute);
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface GrandparentPathProvider<T> extends CapabilityReference.GrandparentPathProvider<T>, GrandparentAttributeProvider<T> {
        /**
         * Specifies the path for the grandparent segment of the requirement.
         * @param path a path element used to construct the capability name pattern for this reference
         * @param resolver a path resolver
         * @return a reference to this builder
         */
        @Override
        default ParentPathProvider<T> withGrandparentPath(PathElement path) {
            return this.withGrandparentPath(path, CapabilityReference.DefaultBuilder.PARENT_PATH);
        }

        /**
         * Specifies the path and resolver for the grandparent segment of the requirement.
         * @param path a path element used to construct the capability name pattern for this reference
         * @param resolver a path resolver
         * @return a reference to this builder
         */
        @Override
        ParentPathProvider<T> withGrandparentPath(PathElement path, Function<PathAddress, PathElement> resolver);
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface GreatGrandparentAttributeProvider<T> extends CapabilityReference.GreatGrandparentAttributeProvider<T> {
        /**
         * Specifies the attribute used to resolves the great-grandparent segment of the requirement.
         * @param attribute the attribute used to resolve the parent segment of the requirement.
         * @return a reference to this builder
         */
        @Override
        GrandparentAttributeProvider<T> withGreatGrandparentAttribute(AttributeDefinition attribute);
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    interface GreatGrandparentPathProvider<T> extends CapabilityReference.GreatGrandparentPathProvider<T>, GreatGrandparentAttributeProvider<T> {
        /**
         * Specifies the path for the great-grandparent segment of the requirement.
         * @param path a path element used to construct the capability name pattern for this reference
         * @return a reference to this builder
         */
        @Override
        default GrandparentPathProvider<T> withGreatGrandparentPath(PathElement path) {
            return this.withGreatGrandparentPath(path, CapabilityReference.DefaultBuilder.GRANDPARENT_PATH);
        }

        /**
         * Specifies the path and resolver for the great-grandparent segment of the requirement.
         * @param path a path element used to construct the capability name pattern for this reference
         * @param resolver a path resolver
         * @return a reference to this builder
         */
        @Override
        GrandparentPathProvider<T> withGreatGrandparentPath(PathElement path, Function<PathAddress, PathElement> resolver);
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    class DefaultBuilder<T> extends CapabilityReference.DefaultBuilder<T> implements GreatGrandparentPathProvider<T>, GrandparentPathProvider<T>, ParentPathProvider<T>, Builder<T> {

        DefaultBuilder(RuntimeCapability<Void> capability, NaryServiceDescriptor<T> requirement) {
            super(capability, requirement);
        }

        @Override
        public GrandparentAttributeProvider<T> withGreatGrandparentAttribute(AttributeDefinition attribute) {
            super.withGreatGrandparentAttribute(attribute);
            return this;
        }

        @Override
        public ParentAttributeProvider<T> withGrandparentAttribute(AttributeDefinition attribute) {
            super.withGrandparentAttribute(attribute);
            return this;
        }

        @Override
        public Builder<T> withParentAttribute(AttributeDefinition attribute) {
            super.withParentAttribute(attribute);
            return this;
        }

        @Override
        public GrandparentPathProvider<T> withGreatGrandparentPath(PathElement path, Function<PathAddress, PathElement> resolver) {
            super.withGreatGrandparentPath(path, resolver);
            return this;
        }

        @Override
        public ParentPathProvider<T> withGrandparentPath(PathElement path, Function<PathAddress, PathElement> resolver) {
            super.withGrandparentPath(path, resolver);
            return this;
        }

        @Override
        public Builder<T> withParentPath(PathElement path, Function<PathAddress, PathElement> resolver) {
            super.withParentPath(path, resolver);
            return this;
        }

        @Override
        public CapabilityReferenceRecorder<T> build() {
            return new CapabilityServiceDescriptorReferenceRecorder<>(super.build());
        }
    }

    @Deprecated(forRemoval = true, since = "26.0.0")
    class CapabilityServiceDescriptorReferenceRecorder<T> implements CapabilityReferenceRecorder<T> {
        private final CapabilityReference<T> reference;

        CapabilityServiceDescriptorReferenceRecorder(CapabilityReference<T> reference) {
            this.reference = reference;
        }

        @Override
        public Map.Entry<String, String[]> resolve(OperationContext context, Resource resource, String value) {
            return this.reference.resolve(context, resource, value);
        }

        @Override
        public RuntimeCapability<Void> getDependent() {
            return this.reference.getDependent();
        }

        @Override
        public ServiceDescriptor<T> getRequirement() {
            return this.reference.getRequirement();
        }

        @Override
        public void addCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues) {
            this.reference.addCapabilityRequirements(context, resource, attributeName, attributeValues);
        }

        @Override
        public void removeCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... attributeValues) {
            this.reference.removeCapabilityRequirements(context, resource, attributeName, attributeValues);
        }

        @Override
        public String[] getRequirementPatternSegments(String name, PathAddress address) {
            return this.reference.getRequirementPatternSegments(name, address);
        }

        @Override
        public int hashCode() {
            return this.reference.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            return this.reference.equals(object);
        }
    }
}
