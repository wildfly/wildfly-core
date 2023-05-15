/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import java.util.UUID;
import java.util.function.Consumer;

import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * @author Paul Ferraro
 */
public class ServiceValueExecutorRegistryTestCase {

    private final ServiceValueExecutorRegistry<Object> registry = ServiceValueExecutorRegistry.create();

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

        Assert.assertNull(this.registry.getExecutor(service1));
        Assert.assertNull(this.registry.getExecutor(service2));

        ExceptionFunction<Object, Object, RuntimeException> function = value -> value;

        Consumer<Object> captor1 = this.registry.add(service1);
        Consumer<Object> captor2 = this.registry.add(service2);

        FunctionExecutor<Object> executor1 = this.registry.getExecutor(service1);
        FunctionExecutor<Object> executor2 = this.registry.getExecutor(service2);

        Assert.assertNull(executor1.execute(function));
        Assert.assertNull(executor2.execute(function));

        captor1.accept(value1);
        captor2.accept(value2);

        Assert.assertSame(value1, executor1.execute(function));
        Assert.assertSame(value2, executor2.execute(function));

        captor1.accept(null);
        captor2.accept(null);

        Assert.assertNull(executor1.execute(function));
        Assert.assertNull(executor2.execute(function));

        captor1.accept(value1);
        captor2.accept(value2);

        // Once removed, executor should return null
        this.registry.remove(service1);
        this.registry.remove(service2);

        Assert.assertNull(this.registry.getExecutor(service1));
        Assert.assertNull(this.registry.getExecutor(service2));

        Assert.assertNull(executor1.execute(function));
        Assert.assertNull(executor2.execute(function));
    }
}
