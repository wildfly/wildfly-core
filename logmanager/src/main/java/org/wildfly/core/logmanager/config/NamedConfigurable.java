/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

/**
 * A configurable object with a name.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface NamedConfigurable {

    /**
     * Get the name of this configurable object.
     *
     * @return the name
     */
    String getName();
}
