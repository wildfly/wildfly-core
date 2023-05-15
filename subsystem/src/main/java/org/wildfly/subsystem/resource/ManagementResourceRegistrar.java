/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Interface implemented by self-registering management components.
 * @author Paul Ferraro
 */
public interface ManagementResourceRegistrar {

    /**
     * Registers this object with a resource.
     * @param registration a registration for a management resource
     */
    void register(ManagementResourceRegistration registration);

    /**
     * Returns a registrar for the given resource descriptor.
     * @param descriptor a resource descriptor
     * @return a registrar for the given resource descriptor.
     */
    static ManagementResourceRegistrar of(ResourceDescriptor descriptor) {
        return new ResourceDescriptorRegistrar(descriptor);
    }
}
