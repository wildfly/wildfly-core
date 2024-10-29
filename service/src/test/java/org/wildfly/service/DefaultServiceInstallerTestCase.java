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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for {@link GenericServiceInstaller}.
 * @author Paul Ferraro
 */
public class DefaultServiceInstallerTestCase {

    @Test
    public void test() throws StartException {
        ServiceTarget target = mock(ServiceTarget.class);
        ServiceBuilder<?> builder = mock(ServiceBuilder.class);
        ServiceController<?> controller = mock(ServiceController.class);
        ServiceName name = ServiceName.JBOSS;
        Consumer<Object> injector = mock(Consumer.class);
        Function<Object, Object> mapper = mock(Function.class);
        ServiceDependency<Object> dependency = mock(ServiceDependency.class);
        Consumer<Object> captor = mock(Consumer.class);
        Consumer<Object> combinedCaptor = mock(Consumer.class);
        Consumer<Object> startConsumer = mock(Consumer.class);
        Consumer<Object> stopConsumer = mock(Consumer.class);
        ArgumentCaptor<Service> service = ArgumentCaptor.forClass(Service.class);
        ArgumentCaptor<ServiceController.Mode> mode = ArgumentCaptor.forClass(ServiceController.Mode.class);
        Map<LifecycleEvent, Runnable> tasks = new EnumMap<>(LifecycleEvent.class);
        for (LifecycleEvent event : EnumSet.of(LifecycleEvent.UP, LifecycleEvent.DOWN, LifecycleEvent.REMOVED)) {
            tasks.put(event, mock(Runnable.class));
        }

        ArgumentCaptor<LifecycleListener> capturedListener = ArgumentCaptor.forClass(LifecycleListener.class);

        doReturn(builder).when(builder).addListener(capturedListener.capture());

        ServiceInstaller installer = ServiceInstaller.builder(mapper, dependency)
                .provides(name)
                .requires(dependency)
                .withCaptor(captor)
                .onStart(startConsumer)
                .onStop(stopConsumer)
                .onStart(tasks.get(LifecycleEvent.UP))
                .onStop(tasks.get(LifecycleEvent.DOWN))
                .onRemove(tasks.get(LifecycleEvent.REMOVED))
                .build();

        doReturn(builder).when(target).addService();
        doReturn(injector).when(builder).provides(name);
        doReturn(combinedCaptor).when(injector).andThen(captor);
        doReturn(builder).when(builder).setInstance(service.capture());
        doReturn(builder).when(builder).setInitialMode(mode.capture());
        doReturn(controller).when(builder).install();

        ServiceController<?> result = installer.install(target);

        Assert.assertSame(controller, result);
        Assert.assertSame(ServiceController.Mode.ON_DEMAND, mode.getValue());

        verify(dependency).accept(builder);
        verify(builder).provides(name);
        for (Runnable task : tasks.values()) {
            verifyNoInteractions(task);
        }

        LifecycleListener listener = capturedListener.getValue();

        Assert.assertNotNull(listener);

        for (LifecycleEvent event : EnumSet.allOf(LifecycleEvent.class)) {
            listener.handleEvent(controller, event);

            for (Map.Entry<LifecycleEvent, Runnable> entry : tasks.entrySet()) {
                if (event == entry.getKey()) {
                    verify(entry.getValue()).run();
                } else {
                    verifyNoMoreInteractions(entry.getValue());
                }
            }
        }

        DefaultServiceTestCase.test(service.getValue(), "value", "mappedValue", combinedCaptor, mapper, dependency, startConsumer, stopConsumer);
    }
}
