/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.function.Consumer;

import org.wildfly.subsystem.service.ServiceDependency;

/**
 * A registry of service values.
 * @author Paul Ferraro
 * @param <V> the captured service value type
 */
public interface ServiceValueRegistry<V> {

    /**
     * Adds a registration for the specified service dependency
     * @param dependency a service dependency
     * @return a consumer to capture the service value
     */
    Consumer<V> add(ServiceDependency<V> dependency);

    /**
     * Removes the registration for the specified service dependency
     * @param dependency a service dependency
     */
    void remove(ServiceDependency<V> dependency);
}
