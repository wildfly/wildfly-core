/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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
        String value = "foo";

        ServiceDependency<Object> dependency = ServiceDependency.of(value);

        dependency.accept(builder);

        verifyNoInteractions(builder);

        Assert.assertSame(value, dependency.get());

        ServiceDependency<Object> mapped = dependency.map(Functions.cast(Function.identity()));

        mapped.accept(builder);

        verifyNoInteractions(builder);

        Assert.assertSame(value, mapped.get());

        ServiceDependency<String> cast = mapped.map(String.class::cast);

        cast.accept(builder);

        verifyNoInteractions(builder);

        Assert.assertSame(value, cast.get());
    }

    @Test
    public void service() {
        ServiceBuilder<?> builder = mock(ServiceBuilder.class);
        ServiceName name = ServiceName.JBOSS;
        Supplier<String> injection1 = Functions.constantSupplier("foo");
        Supplier<String> injection2 = Functions.constantSupplier("bar");
        Supplier<String> injection3 = Functions.constantSupplier("qux");

        ServiceDependency<Object> dependency = ServiceDependency.on(name);

        doReturn(injection1, injection2, injection3).when(builder).requires(name);

        dependency.accept(builder);

        Assert.assertSame(injection1.get(), dependency.get());

        ServiceDependency<Object> mapped = dependency.map(Functions.cast(Function.identity()));

        Assert.assertSame(injection1.get(), mapped.get());

        mapped.accept(builder);

        Assert.assertSame(injection2.get(), mapped.get());
        Assert.assertSame(injection2.get(), dependency.get());

        ServiceDependency<String> cast = mapped.map(String.class::cast);

        Assert.assertSame(injection2.get(), cast.get());

        cast.accept(builder);

        Assert.assertSame(injection3.get(), cast.get());
        Assert.assertSame(injection3.get(), mapped.get());
        Assert.assertSame(injection3.get(), dependency.get());
    }

    @Test
    public void thenAccept() {
        UUID dependencyValue = UUID.randomUUID();
        UUID otherValue = UUID.randomUUID();
        ServiceDependency<UUID> dependency = ServiceDependency.of(dependencyValue);
        Consumer<UUID> unaryConsumer = mock(Consumer.class);

        Runnable runner = dependency.thenAccept(unaryConsumer);

        verifyNoInteractions(unaryConsumer);

        runner.run();

        verify(unaryConsumer).accept(dependencyValue);
        verifyNoMoreInteractions(unaryConsumer);

        BiConsumer<UUID, UUID> binaryConsumer = mock(BiConsumer.class);

        Consumer<UUID> consumer = dependency.thenAccept(binaryConsumer);

        verifyNoInteractions(binaryConsumer);

        consumer.accept(otherValue);

        verify(binaryConsumer).accept(dependencyValue, otherValue);
        verifyNoMoreInteractions(binaryConsumer);
    }
}
