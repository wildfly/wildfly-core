/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

import java.util.function.Consumer;

/**
 * A registry of values.
 * @author Paul Ferraro
 * @param <K> the registry key type
 * @param <V> the registry value type
 */
public interface ValueRegistry<K, V> {

    /**
     * Adds a value registration for the specified key
     * @param key a registry key
     * @return a consumer to capture the value
     */
    Consumer<V> add(K key);

    /**
     * Removes the registration for the specified key
     * @param key a registry key
     */
    void remove(K key);
}
