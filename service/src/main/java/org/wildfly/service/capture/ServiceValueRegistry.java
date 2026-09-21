/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

import java.util.function.Consumer;

import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.service.BlockingLifecycle;
import org.wildfly.service.ServiceDependency;
import org.wildfly.service.ServiceInstaller;

/**
 * The provider interface for a registry of captured values provided by MSC services.
 * @author Paul Ferraro
 * @param <V> the registry value type
 */
public interface ServiceValueRegistry<V> extends ValueRegistry<ServiceName, V>, ServiceValueCaptor<ServiceName, ServiceTarget> {

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
        return ServiceInstaller.BlockingBuilder.of(ServiceDependency.<V>on(name))
                .withLifecycle(BlockingLifecycle.compose(start, stop))
                .build();
    }
}
