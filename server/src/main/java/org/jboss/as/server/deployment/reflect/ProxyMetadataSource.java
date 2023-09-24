/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.reflect;

import org.jboss.invocation.proxy.reflection.ClassMetadataSource;
import org.jboss.invocation.proxy.reflection.ReflectionMetadataSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * @author Stuart Douglas
 */
public class ProxyMetadataSource implements ReflectionMetadataSource {

    private final DeploymentReflectionIndex index;

    public ProxyMetadataSource(final DeploymentReflectionIndex index) {
        this.index = index;
    }

    @Override
    public ClassMetadataSource getClassMetadata(final Class<?> clazz) {
        final ClassReflectionIndex index = this.index.getClassIndex(clazz);
        return new ClassMetadataSource() {
            @Override
            public Collection<Method> getDeclaredMethods() {
                return index.getClassMethods();
            }

            @Override
            public Method getMethod(final String methodName, final Class<?> returnType, final Class<?>... parameters) throws NoSuchMethodException {
                return index.getMethod(returnType, methodName, parameters);
            }

            @Override
            @SuppressWarnings("unchecked")
            public Collection<Constructor<?>> getConstructors() {
                return (Collection) index.getConstructors();
            }
        };
    }
}
