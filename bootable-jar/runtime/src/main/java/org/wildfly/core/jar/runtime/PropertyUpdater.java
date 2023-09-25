/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.jar.runtime;

/**
 * A simple interface for setting properties.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@FunctionalInterface
interface PropertyUpdater {

    /**
     * Sets the property.
     *
     * @param name  the name of the property
     * @param value the value of the property
     *
     * @return the previous value if the property existed or {@code null} if the property did not already exist
     */
    String setProperty(String name, String value);
}
