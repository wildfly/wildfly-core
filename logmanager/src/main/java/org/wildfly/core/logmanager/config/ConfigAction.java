/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import org.jboss.logmanager.configuration.ConfigurationResource;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
interface ConfigAction<T> {
    T validate() throws IllegalArgumentException;
    default T validate(ConfigurationResource<T> resource) {
        return resource.get();
    }

    void applyPreCreate(T param);

    void applyPostCreate(T param);

    void rollback();
}
