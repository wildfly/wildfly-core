/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import java.util.Map;

/**
 * A general purpose interface that can be implemented by custom implementations being plugged into the Elytron subsystem.
 *
 * Where custom components implement this interface they can be dynamically be configured by the subsystem with
 * a {@link Map<String, String>}.
 *
 * @deprecated it is sufficient to implement {@code initialize(Map&lt;String, String&gt;)} method in custom component
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@Deprecated
public interface Configurable {

    /**
     * Initialize the {@link Configurable} class with the specified options.
     *
     * @param configuration
     */
    void initialize(final Map<String, String> configuration);

}
