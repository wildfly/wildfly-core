/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.capability;

import java.util.function.Supplier;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.service.descriptor.ServiceDescriptor;

/**
 * @author Paul Ferraro
 */
public interface RuntimeCapabilityProvider<T> extends Supplier<RuntimeCapability<Void>>, ServiceDescriptor<T> {

    @Override
    default String getName() {
        return this.get().getName();
    }

    @SuppressWarnings("unchecked")
    @Override
    default Class<T> getType() {
        return (Class<T>) this.get().getCapabilityServiceValueType();
    }
}
