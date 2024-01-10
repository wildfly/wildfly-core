/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.descriptor;

import java.util.function.Supplier;

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

    /**
     * Returns a sub-class view of this service descriptor.
     * @param <U> the subclass type
     * @param type a sub-class of this descriptor's type
     * @return a sub-class view of this service descriptor.
     */
    <U extends T> ServiceDescriptor<U> asType(Class<U> type);

    /**
     * Provides a service descriptor.
     * Typically implemented by enumerations providing service descriptors of the same type.
     * @param <T> the service value type
     * @param <SD> the provided service descriptor type
     */
    interface Provider<T, SD extends ServiceDescriptor<T>> extends Supplier<SD>, ServiceDescriptor<T> {
        @Override
        default String getName() {
            return this.get().getName();
        }

        @Override
        default Class<T> getType() {
            return this.get().getType();
        }
    }
}
