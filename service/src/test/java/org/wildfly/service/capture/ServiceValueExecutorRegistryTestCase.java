/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.capture;

import java.util.UUID;
import java.util.function.Consumer;

import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.function.ExceptionFunction;

/**
 * @author Paul Ferraro
 */
public class ServiceValueExecutorRegistryTestCase {

    private final ServiceValueExecutorRegistry<Object> registry = ServiceValueExecutorRegistry.newInstance();

    @Test
    public void test() {
        this.test(ServiceName.JBOSS.append("foo"), ServiceName.JBOSS.append("bar"));
    }

    private void test(ServiceName service1, ServiceName service2) {
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
