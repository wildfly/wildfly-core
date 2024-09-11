/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.Map;

import org.wildfly.common.Assert;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.ServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * A variable segment service descriptor.
 * @param <T> the service type
 */
interface NaryServiceDescriptor<T> extends ServiceDescriptor<T> {

    /**
     * Resolves the specified reference segments.
     * @param references a number of reference segments.
     * @return a map entry of name and segments.
     */
    Map.Entry<String, String[]> resolve(String[] references);

    static <T> NaryServiceDescriptor<T> of(NullaryServiceDescriptor<T> descriptor) {
        return new NaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return descriptor.getName();
            }

            @Override
            public Class<T> getType() {
                return descriptor.getType();
            }

            @Override
            public <U extends T> NaryServiceDescriptor<U> asType(Class<U> type) {
                return of(descriptor.asType(type));
            }

            @Override
            public Map.Entry<String, String[]> resolve(String[] references) {
                Assert.checkArrayBounds(references, 0, 0);
                return descriptor.resolve();
            }
        };
    }

    static <T> NaryServiceDescriptor<T> of(UnaryServiceDescriptor<T> descriptor) {
        return new NaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return descriptor.getName();
            }

            @Override
            public Class<T> getType() {
                return descriptor.getType();
            }

            @Override
            public <U extends T> NaryServiceDescriptor<U> asType(Class<U> type) {
                return of(descriptor.asType(type));
            }

            @Override
            public Map.Entry<String, String[]> resolve(String[] references) {
                Assert.checkArrayBounds(references, 0, 1);
                return descriptor.resolve(references[0]);
            }
        };
    }

    static <T> NaryServiceDescriptor<T> of(BinaryServiceDescriptor<T> descriptor) {
        return new NaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return descriptor.getName();
            }

            @Override
            public Class<T> getType() {
                return descriptor.getType();
            }

            @Override
            public <U extends T> NaryServiceDescriptor<U> asType(Class<U> type) {
                return of(descriptor.asType(type));
            }

            @Override
            public Map.Entry<String, String[]> resolve(String[] references) {
                Assert.checkArrayBounds(references, 0, 2);
                return descriptor.resolve(references[0], references[1]);
            }
        };
    }

    static <T> NaryServiceDescriptor<T> of(TernaryServiceDescriptor<T> descriptor) {
        return new NaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return descriptor.getName();
            }

            @Override
            public Class<T> getType() {
                return descriptor.getType();
            }

            @Override
            public <U extends T> NaryServiceDescriptor<U> asType(Class<U> type) {
                return of(descriptor.asType(type));
            }

            @Override
            public Map.Entry<String, String[]> resolve(String[] references) {
                Assert.checkArrayBounds(references, 0, 3);
                return descriptor.resolve(references[0], references[1], references[2]);
            }
        };
    }

    static <T> NaryServiceDescriptor<T> of(QuaternaryServiceDescriptor<T> descriptor) {
        return new NaryServiceDescriptor<>() {
            @Override
            public String getName() {
                return descriptor.getName();
            }

            @Override
            public Class<T> getType() {
                return descriptor.getType();
            }

            @Override
            public <U extends T> NaryServiceDescriptor<U> asType(Class<U> type) {
                return of(descriptor.asType(type));
            }

            @Override
            public Map.Entry<String, String[]> resolve(String[] references) {
                Assert.checkArrayBounds(references, 0, 4);
                return descriptor.resolve(references[0], references[1], references[2], references[3]);
            }
        };
    }
}
