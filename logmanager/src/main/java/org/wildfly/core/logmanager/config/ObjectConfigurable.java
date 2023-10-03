/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

/**
 * A configurable object with a specific class name.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ObjectConfigurable<T> {

    /**
     * Get the module name for this object's configuration, if any.  If JBoss Modules is not present
     * on the class path, only {@code null} values are accepted.
     *
     * @return the module name, or {@code null} if none is configured
     */
    String getModuleName();

    /**
     * Get the class name for this object's configuration.
     *
     * @return the class name
     */
    String getClassName();

    /**
     * Returns the instance associated with this configuration or {@code null} if no instance has yet been created.
     * <p>
     * Any changes to the instance will not be recognized by the configuration API.
     * </p>
     *
     * @return the instance associated with this configuration or {@code null}
     */
    T getInstance();
}
