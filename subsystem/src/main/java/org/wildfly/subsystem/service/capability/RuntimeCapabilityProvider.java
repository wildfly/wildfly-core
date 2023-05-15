/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capability;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.service.descriptor.ServiceDescriptor;

/**
 * @author Paul Ferraro
 */
public interface RuntimeCapabilityProvider<T> extends ServiceDescriptor<T> {

    RuntimeCapability<Void> getCapability();

    @Override
    default String getName() {
        return this.getCapability().getName();
    }

    @SuppressWarnings("unchecked")
    @Override
    default Class<T> getType() {
        return (Class<T>) this.getCapability().getCapabilityServiceValueType();
    }
}
