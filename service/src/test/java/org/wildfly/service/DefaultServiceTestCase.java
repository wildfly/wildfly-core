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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.junit.Test;

/**
 * Unit test for {@link DefaultService}.
 * @author Paul Ferraro
 */
public class DefaultServiceTestCase {

    @Test
    public void test() throws StartException {
        Consumer<Object> injector = mock(Consumer.class);
        Function<Object, Object> mapper = mock(Function.class);
        Supplier<Object> factory = mock(Supplier.class);
        Consumer<Object> initializer = mock(Consumer.class);
        Consumer<Object> destroyer = mock(Consumer.class);
        Object value = "value";
        Object mappedValue = "mapped";

        test(new Installer.UnaryService<>(injector, mapper, factory, initializer, destroyer), value, mappedValue, injector, mapper, factory, initializer, destroyer);
    }

    static <T, V> void test(Service service, T value, V mappedValue, Consumer<V> consumer, Function<T, V> mapper, Supplier<T> factory, Consumer<T> startTask, Consumer<T> stopTask) throws StartException {
        StartContext startContext = mock(StartContext.class);

        doReturn(value).when(factory).get();
        doReturn(mappedValue).when(mapper).apply(value);

        service.start(startContext);

        verify(consumer).accept(mappedValue);
        verify(startTask).accept(value);
        verifyNoInteractions(startContext);
        verifyNoInteractions(stopTask);

        StopContext stopContext = mock(StopContext.class);

        service.stop(stopContext);

        verify(consumer).accept(null);
        verifyNoMoreInteractions(startTask);
        verify(stopTask).accept(value);
        verifyNoInteractions(stopContext);
    }
}
