/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.function.Supplier;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.function.Functions;

/**
 * Unit test for {@link SimpleServiceDependencySupplier}.
 * @author Paul Ferraro
 */
public class ServiceDependencyTestCase {

    @Test
    public void simple() {
        ServiceBuilder<?> builder = mock(ServiceBuilder.class);
        Object value = new Object();

        ServiceDependency<Object> dependency = ServiceDependency.of(value);

        dependency.accept(builder);

        Assert.assertSame(value, dependency.get());
    }

    @Test
    public void service() {
        ServiceBuilder<?> builder = mock(ServiceBuilder.class);
        ServiceName name = ServiceName.JBOSS;
        Supplier<Object> injection = Functions.constantSupplier(new Object());

        ServiceDependency<Object> dependency = ServiceDependency.on(name);

        doReturn(injection).when(builder).requires(name);

        dependency.accept(builder);

        Assert.assertSame(injection.get(), dependency.get());
    }
}
