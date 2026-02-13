/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.service.capability;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.ServiceName;

/**
 * @author Paul Ferraro
 */
class RuntimeCapabilityProvider<V> implements BiFunction<CapabilityServiceBuilder<?>, Collection<ServiceName>, Consumer<V>> {
    private final RuntimeCapability<Void> capability;

    RuntimeCapabilityProvider(RuntimeCapability<Void> capability) {
        this.capability = capability;
    }

    @Override
    public Consumer<V> apply(CapabilityServiceBuilder<?> builder, Collection<ServiceName> names) {
        return names.isEmpty() ? builder.provides(this.capability) : builder.provides(List.of(this.capability).toArray(RuntimeCapability[]::new), names.toArray(ServiceName[]::new));
    }
}
