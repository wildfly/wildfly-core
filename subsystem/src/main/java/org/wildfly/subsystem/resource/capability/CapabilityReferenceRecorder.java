/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A {@link org.jboss.as.controller.CapabilityReferenceRecorder} whose requirement is specified as a {@link org.wildfly.service.descriptor.ServiceDescriptor}.
 * @param <T> the requirement type
 * @deprecated Replaced by {@link CapabilityReference}.
 */
@Deprecated(forRemoval = true)
public interface CapabilityReferenceRecorder<T> extends CapabilityReference<T> {

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> Builder<T> builder(RuntimeCapability<Void> capability, UnaryServiceDescriptor<T> requirement) {
        return CapabilityReference.builder(capability, requirement);
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's parent segment derives from the path of the current resource.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> ParentPathProvider<T> builder(RuntimeCapability<Void> capability, BinaryServiceDescriptor<T> requirement) {
        return CapabilityReference.builder(capability, requirement);
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's grandparent and parent segments derive from the path of the parent and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> GrandparentPathProvider<T> builder(RuntimeCapability<Void> capability, TernaryServiceDescriptor<T> requirement) {
        return CapabilityReference.builder(capability, requirement);
    }

    /**
     * Creates a new reference between the specified capability and the specified requirement.
     * By default, the requirement's great-grandparent, grandparent, and parent segments derive from the path of the grandparent, parent, and current resources, respectively.
     * @param capability the capability referencing the specified requirement
     * @param requirement the requirement of the specified capability
     */
    static <T> GreatGrandparentPathProvider<T> builder(RuntimeCapability<Void> capability, QuaternaryServiceDescriptor<T> requirement) {
        return CapabilityReference.builder(capability, requirement);
    }

}
