/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

/**
 * Registry of {@link FunctionExecutor} objects.
 * @author Paul Ferraro
 * @param <K> the registry key type
 * @param <V> the registry value type
 */
public interface FunctionExecutorRegistry<K, V> {
    /**
     * Returns the executor for the specified key, if one exists.
     * @param key a registry key
     * @return the executor for the specified key, or null, if no such executor exists in the registry
     */
    FunctionExecutor<V> getExecutor(K key);
}
