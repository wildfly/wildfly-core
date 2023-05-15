/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * A registry of service values.
 * @author Paul Ferraro
 * @param <V> the captured service value type
 */
public interface ResourceServiceValueRegistry<V> {

    /**
     * Creates a service installer to capture the value provided by the specified service dependency.
     * @param dependency a service dependency
     * @return a service installer
     */
    ResourceServiceInstaller capture(ServiceDependency<V> dependency);
}
