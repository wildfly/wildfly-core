/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Registry of {@link FunctionExecutor} objects.
 * @author Paul Ferraro
 * @param <V> the argument type of the function executor
 */
public interface FunctionExecutorRegistry<V> {
    /**
     * Returns the function executor for the value provided by the specified dependency
     * @param dependency a service dependency
     * @return a function executor
     */
    FunctionExecutor<V> getExecutor(ServiceDependency<V> dependency);
}
