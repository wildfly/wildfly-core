/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A {@link CapabilityReference} specialization that records requirements of a resource, rather than an attribute.
 * @param <T> the requirement type
 * @deprecated Replaced by {@link ResourceCapabilityReference}.
 */
@Deprecated(forRemoval = true)
public interface ResourceCapabilityReferenceRecorder<T> extends ResourceCapabilityReference<T> {

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> Builder<T> builder(RuntimeCapability<Void> capability, NullaryServiceDescriptor<T> requirement) {
        return ResourceCapabilityReference.builder(capability, requirement);
    }

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * By default, the requirement name will resolve against the path of the current resource.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> NaryBuilder<T> builder(RuntimeCapability<Void> capability, UnaryServiceDescriptor<T> requirement) {
        return ResourceCapabilityReference.builder(capability, requirement);
    }

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * By default, the requirement name will resolve against the paths of the parent and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param requirementNameResolver function for resolving the dynamic components of the requirement name
     */
    static <T> NaryBuilder<T> builder(RuntimeCapability<Void> capability, BinaryServiceDescriptor<T> requirement) {
        return ResourceCapabilityReference.builder(capability, requirement);
    }

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * By default, the requirement name will resolve against the paths of the grandparent, parent, and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param requirementNameResolver function for resolving the dynamic components of the requirement name
     */
    static <T> NaryBuilder<T> builder(RuntimeCapability<Void> capability, TernaryServiceDescriptor<T> requirement) {
        return ResourceCapabilityReference.builder(capability, requirement);
    }

    /**
     * Creates a builder for a new reference between the specified capability and the specified requirement.
     * By default, the requirement name will resolve against the paths of the great-grandparent, grandparent, parent, and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     * @param requirementNameResolver function for resolving the dynamic components of the requirement name
     */
    static <T> NaryBuilder<T> builder(RuntimeCapability<Void> capability, QuaternaryServiceDescriptor<T> requirement) {
        return ResourceCapabilityReference.builder(capability, requirement);
    }
}
