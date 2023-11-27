/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.descriptor;

/**
 * Describes a service by its name and provided value type.
 * @author Paul Ferraro
 * @param <T> the type of the value provided by the described service
 */
public interface ServiceDescriptor<T> {

    /**
     * Returns the name of this described service.
     * @return the name of this described service.
     */
    String getName();

    /**
     * Returns the provided value type of this described service.
     * @return the provided value type of this described service.
     */
    Class<T> getType();
}
