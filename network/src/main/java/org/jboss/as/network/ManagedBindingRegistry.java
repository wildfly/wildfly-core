/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.network;

import java.util.Collection;

/**
 * @author Emanuel Muckenhuber
 */
public interface ManagedBindingRegistry {

    /**
     * Register a managed binding.
     *
     * @param binding the managed binding
     */
    void registerBinding(final ManagedBinding binding);

    /**
     * Unregister a managed binding.
     *
     * @param binding the managed binding
     */
    void unregisterBinding(final ManagedBinding binding);

    /**
     * List the activate bindings.
     *
     * @return the registered bindings
     */
    Collection<ManagedBinding> listActiveBindings();

}
