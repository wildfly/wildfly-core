/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.msc.service.ServiceName;
import org.wildfly.service.BlockingLifecycle;
import org.wildfly.service.capture.ServiceValueCaptor;
import org.wildfly.service.capture.ValueRegistry;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * A registry of service values.
 * @author Paul Ferraro
 * @param <V> the captured service value type
 */
public interface ServiceValueRegistry<V> extends ValueRegistry<ServiceName, V>, ServiceValueCaptor<ServiceName, RequirementServiceTarget> {

    /**
     * @deprecated Superseded by {@link #add(Object)}.
     */
    @Deprecated
    default Consumer<V> add(ServiceDependency<V> key) {
        AtomicReference<ServiceName> reference = new AtomicReference<>();
        key.accept(new ServiceNameCapturingServiceBuilder(reference));
        return this.add(reference.getPlain());
    }

    /**
     * @deprecated Superseded by {@link #remove(Object)}.
     */
    @Deprecated
    default void remove(ServiceDependency<V> key) {
        AtomicReference<ServiceName> reference = new AtomicReference<>();
        key.accept(new ServiceNameCapturingServiceBuilder(reference));
        this.remove(reference.getPlain());
    }

    /**
     * @deprecated Superseded by {@link #capture(Object)}.
     */
    @Deprecated
    default ServiceInstaller capture(ServiceDependency<V> key) {
        AtomicReference<ServiceName> reference = new AtomicReference<>();
        key.accept(new ServiceNameCapturingServiceBuilder(reference));
        return this.capture(reference.getPlain());
    }

    /**
     * Creates a service installer to capture and release the value provided by the specified service dependency.
     * @param dependency a service dependency
     * @return a service installer
     */
    @Override
    default ServiceInstaller capture(ServiceName name) {
        Consumer<V> start = new Consumer<>() {
            @Override
            public void accept(V value) {
                ServiceValueRegistry.this.add(name).accept(value);
            }
        };
        Consumer<V> stop = new Consumer<>() {
            @Override
            public void accept(V value) {
                ServiceValueRegistry.this.remove(name);
            }
        };
        return ServiceInstaller.BlockingBuilder.of(ServiceDependency.<V>on(name)).withLifecycle(BlockingLifecycle.compose(start, stop)).build();
    }
}
