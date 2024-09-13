/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.registry.Resource;
import org.wildfly.service.descriptor.ServiceDescriptor;

/**
 * Resolves a capability reference.
 * @param <T> the requirement type
 */
public interface CapabilityReferenceResolver<T> {
    /**
     * Returns the service descriptor required by the dependent capability.
     * @return a service descriptor
     */
    ServiceDescriptor<T> getRequirement();

    /**
     * Resolves the dynamic segments of this capability reference.
     * @param context an operation context
     * @param resource the resource
     * @param value the attribute value
     * @return a map entry containing the requirement name and array of dynamic name segments
     */
    Map.Entry<String, String[]> resolve(OperationContext context, Resource resource, String value);
}
