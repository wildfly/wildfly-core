/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.function.Consumer;

import org.wildfly.service.BlockingLifecycle;
import org.wildfly.service.capture.ValueRegistry;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * A registry of service values.
 * @author Paul Ferraro
 * @param <V> the captured service value type
 */
public interface ServiceValueRegistry<V> extends ValueRegistry<ServiceDependency<V>, V> {

    /**
     * Creates a service installer to capture and release the value provided by the specified service dependency.
     * @param dependency a service dependency
     * @return a service installer
     */
    default ServiceInstaller capture(ServiceDependency<V> dependency) {
        Consumer<V> start = new Consumer<>() {
            @Override
            public void accept(V value) {
                ServiceValueRegistry.this.add(dependency).accept(value);
            }
        };
        Consumer<V> stop = new Consumer<>() {
            @Override
            public void accept(V value) {
                ServiceValueRegistry.this.remove(dependency);
            }
        };
        return ServiceInstaller.BlockingBuilder.of(dependency).withLifecycle(BlockingLifecycle.compose(start, stop)).build();
    }
}
