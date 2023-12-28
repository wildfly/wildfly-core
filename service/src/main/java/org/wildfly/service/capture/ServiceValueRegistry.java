/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

import java.util.function.Consumer;

import org.jboss.msc.service.ServiceName;
import org.wildfly.service.ServiceDependency;
import org.wildfly.service.ServiceInstaller;

/**
 * A registry of service values, keyed by {@link ServiceName}.
 * @author Paul Ferraro
 * @param <V> the registry value type
 */
public interface ServiceValueRegistry<V> extends ValueRegistry<ServiceName, V> {

    /**
     * Creates a service installer to capture and release the value provided by the specified service dependency.
     * @param dependency a service dependency
     * @return a service installer
     */
    default ServiceInstaller capture(ServiceName name) {
        Consumer<V> startTask = new Consumer<>() {
            @Override
            public void accept(V value) {
                ServiceValueRegistry.this.add(name).accept(value);
            }
        };
        Consumer<V> stopTask = new Consumer<>() {
            @Override
            public void accept(V value) {
                ServiceValueRegistry.this.remove(name);
            }
        };
        ServiceDependency<V> dependency = ServiceDependency.on(name);
        return ServiceInstaller.builder(dependency).onStart(startTask).onStop(stopTask).build();
    }
}
