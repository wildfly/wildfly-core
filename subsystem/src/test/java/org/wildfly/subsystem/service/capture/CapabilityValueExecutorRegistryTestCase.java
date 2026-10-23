/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service.capture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.junit.Test;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.service.capture.FunctionExecutor;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Unit test for {@link CapabilityValueExecutorRegistry}
 * @author Paul Ferraro
 */
public class CapabilityValueExecutorRegistryTestCase {

    @Test
    public void test() {
        this.test(RuntimeCapability.Builder.of(NullaryServiceDescriptor.of("test", Object.class)).build());
        this.test(RuntimeCapability.Builder.of(UnaryServiceDescriptor.of("test", Object.class)).build());
        this.test(RuntimeCapability.Builder.of(BinaryServiceDescriptor.of("test", Object.class)).build());
        this.test(RuntimeCapability.Builder.of(TernaryServiceDescriptor.of("test", Object.class)).build());
        this.test(RuntimeCapability.Builder.of(QuaternaryServiceDescriptor.of("test", Object.class)).build());
    }

    private void test(RuntimeCapability<Void> capability) {
        PathAddress address = PathAddress.pathAddress(List.of(PathElement.pathElement("great-grand-parent", "foo"), PathElement.pathElement("grand-parent", "bar"), PathElement.pathElement("parent", "baz"), PathElement.pathElement("child", "qux")));
        CapabilityValueExecutorRegistry<Object> registry = CapabilityValueExecutorRegistry.of(capability);
        ExceptionFunction<Object, Object, RuntimeException> function = mock(ExceptionFunction.class);

        // Verify executor evaluates to null if not registered
        FunctionExecutor<Object> executor = registry.getExecutor(address);

        assertNotNull(executor);
        assertNull(executor.execute(function));
        assertNull(registry.getExecutor(address).execute(function));

        verifyNoInteractions(function);

        Object value1 = UUID.randomUUID();
        Object expected1 = UUID.randomUUID();

        doReturn(expected1).when(function).apply(value1);

        Consumer<Object> consumer1 = registry.add(address);

        // Executor should still evaluate to null until value is provided
        assertNull(executor.execute(function));
        assertNull(registry.getExecutor(address).execute(function));

        verifyNoInteractions(function);

        // Verify executor returns expected value once provided
        consumer1.accept(value1);

        assertSame(expected1, executor.execute(function));

        verify(function).apply(value1);
        verifyNoMoreInteractions(function);

        Object value2 = UUID.randomUUID();
        Object expected2 = UUID.randomUUID();
        doReturn(expected2).when(function).apply(value2);

        // Verify executor reflects updated value
        consumer1.accept(value2);

        assertSame(expected2, executor.execute(function));

        verify(function).apply(value2);
        verifyNoMoreInteractions(function);

        // Verify registry removal
        registry.remove(address);

        // Executor should evaluate to null again
        assertNull(executor.execute(function));
        assertNull(registry.getExecutor(address).execute(function));

        verifyNoMoreInteractions(function);

        // Verify that orphan Consumer cannot modify registry
        consumer1.accept(UUID.randomUUID());

        assertNull(executor.execute(function));
        assertNull(registry.getExecutor(address).execute(function));

        verifyNoMoreInteractions(function);

        // Verify a new consumer
        Consumer<Object> consumer2 = registry.add(address);

        consumer2.accept(value1);

        assertSame(expected1, executor.execute(function));

        verify(function, times(2)).apply(value1);
        verifyNoMoreInteractions(function);

        // Verify that orphan Consumer cannot modify registry
        consumer1.accept(UUID.randomUUID());

        assertSame(expected1, executor.execute(function));

        verify(function, times(3)).apply(value1);
        verifyNoMoreInteractions(function);

        // Verify registry removal
        registry.remove(address);

        assertNull(executor.execute(function));
        assertNull(registry.getExecutor(address).execute(function));

        verifyNoMoreInteractions(function);

        // Verify that orphan Consumers cannot modify registry
        consumer1.accept(UUID.randomUUID());

        assertNull(executor.execute(function));
        assertNull(registry.getExecutor(address).execute(function));

        verifyNoMoreInteractions(function);

        consumer2.accept(UUID.randomUUID());

        assertNull(executor.execute(function));
        assertNull(registry.getExecutor(address).execute(function));

        verifyNoMoreInteractions(function);
    }
}
