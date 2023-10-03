/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.Collection;
import java.util.List;

/**
 * A configurable object which is a container for handlers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface HandlerContainingConfigurable {

    /**
     * Get the names of the configured handlers.
     *
     * @return the names of the configured handlers
     */
    List<String> getHandlerNames();

    /**
     * Set the names of the configured handlers.
     *
     * @param names the names of the configured handlers
     */
    void setHandlerNames(String... names);

    /**
     * Set the names of the configured handlers.
     *
     * @param names the names of the configured handlers
     */
    void setHandlerNames(Collection<String> names);

    /**
     * Add a handler name to this logger.
     *
     * @param name the handler name
     * @return {@code true} if the name was not already set, {@code false} if it was
     */
    boolean addHandlerName(String name);

    /**
     * Remove a handler name from this logger.
     *
     * @param name the handler name
     * @return {@code true} if the name was removed, {@code false} if it was not present
     */
    boolean removeHandlerName(String name);

}
