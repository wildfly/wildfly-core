/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.function.Consumer;

import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.service.capture.FunctionExecutor;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * @author Paul Ferraro
 */
public class ServiceValueExecutorRegistryTestCase {

    private final ServiceValueExecutorRegistry<Object> registry = ServiceValueExecutorRegistry.newInstance();

    @Test
    public void test() {
        this.test(ServiceDependency.on(ServiceName.JBOSS.append("foo")), ServiceDependency.on(ServiceName.JBOSS.append("bar")));
        this.test(ServiceDependency.on(NullaryServiceDescriptor.of("foo", Object.class)), ServiceDependency.on(NullaryServiceDescriptor.of("bar", Object.class)));
        this.test(ServiceDependency.on(UnaryServiceDescriptor.of("test", Object.class), "foo"), ServiceDependency.on(UnaryServiceDescriptor.of("test", Object.class), "bar"));
        this.test(ServiceDependency.on(BinaryServiceDescriptor.of("test", Object.class), "foo", "bar"), ServiceDependency.on(BinaryServiceDescriptor.of("test", Object.class), "foo", "baz"));
        this.test(ServiceDependency.on(TernaryServiceDescriptor.of("test", Object.class), "foo", "bar", "baz"), ServiceDependency.on(TernaryServiceDescriptor.of("test", Object.class), "foo", "bar", "qux"));
    }

    private void test(ServiceDependency<Object> service1, ServiceDependency<Object> service2) {
        Object value1 = UUID.randomUUID();
        Object value2 = UUID.randomUUID();

        FunctionExecutor<Object> executor1 = this.registry.getExecutor(service1);
        FunctionExecutor<Object> executor2 = this.registry.getExecutor(service2);

        ExceptionFunction<Object, Object, RuntimeException> function = mock(ExceptionFunction.class);

        Assert.assertNull(executor1.execute(function));
        Assert.assertNull(executor2.execute(function));

        verifyNoInteractions(function);

        Consumer<Object> captor1 = this.registry.add(service1);
        Consumer<Object> captor2 = this.registry.add(service2);

        Assert.assertNull(executor1.execute(function));
        Assert.assertNull(executor2.execute(function));

        verifyNoInteractions(function);

        captor1.accept(value1);
        captor2.accept(value2);

        Object expected1 = UUID.randomUUID();
        Object expected2 = UUID.randomUUID();

        doReturn(expected1).when(function).apply(value1);
        doReturn(expected2).when(function).apply(value2);

        Assert.assertSame(expected1, executor1.execute(function));
        Assert.assertSame(expected2, executor2.execute(function));

        // Once removed, executor should return null
        this.registry.remove(service1);
        this.registry.remove(service2);

        Assert.assertNull(executor1.execute(function));
        Assert.assertNull(executor2.execute(function));
    }
}
