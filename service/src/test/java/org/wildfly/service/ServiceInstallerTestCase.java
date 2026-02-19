/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.wildfly.common.function.Functions;

/**
 * Unit test for ServiceInstaller implementations.
 * @author Paul Ferraro
 */
public class ServiceInstallerTestCase {

    @Test
    public void modes() throws StartException {
        this.mode(Installer.StartWhen.AVAILABLE, ServiceController.Mode.PASSIVE);
        this.mode(Installer.StartWhen.INSTALLED, ServiceController.Mode.ACTIVE);
        this.mode(Installer.StartWhen.REQUIRED, ServiceController.Mode.ON_DEMAND);
    }

    private void mode(Installer.StartWhen when, ServiceController.Mode expectedInitialMode) {
        ServiceBuilder<?> builder = mock(ServiceBuilder.class);

        verifyInstall(ServiceInstaller.BlockingBuilder.of(Functions.constantSupplier("foo")).startWhen(when).build(), builder);

        verify(builder).setInitialMode(expectedInitialMode);
    }

    @Test
    public void sync() throws StartException {
        ServiceDependency<Object> dependency = mock(ServiceDependency.class);
        Function<Object, BlockingLifecycle> lifecycleProvider = mock(Function.class);
        BlockingLifecycle lifecycle = mock(BlockingLifecycle.class);
        Consumer<Object> captor = mock(Consumer.class);

        doCallRealMethod().when(captor).andThen(any());

        ServiceInstaller installer = ServiceInstaller.BlockingBuilder.of(dependency).withLifecycle(lifecycleProvider).withCaptor(captor).build();

        verifyNoInteractions(dependency);
        verify(captor, atMostOnce()).andThen(any());
        verifyNoMoreInteractions(captor);

        ServiceBuilder<?> builder = mock(ServiceBuilder.class);

        org.jboss.msc.Service service = verifyInstall(installer, builder);

        verify(dependency, only()).accept(builder);
        // Default mode should be PASSIVE, since service does not provide a value
        verify(builder).setInitialMode(ServiceController.Mode.PASSIVE);

        StartContext startContext = mock(StartContext.class);
        InOrder startOrder = inOrder(lifecycle, captor);
        Object value = new Object();

        doReturn(value).when(dependency).get();
        doReturn(lifecycle).when(lifecycleProvider).apply(value);
        doReturn(true).when(lifecycle).isStopped();

        service.start(startContext);

        verifyNoInteractions(startContext);
        verify(dependency).get();
        verifyNoMoreInteractions(dependency);
        verify(lifecycleProvider, only()).apply(value);
        startOrder.verify(lifecycle).isStopped();
        startOrder.verify(lifecycle).start();
        verifyNoMoreInteractions(lifecycle);
        startOrder.verify(captor).accept(value);
        verifyNoMoreInteractions(captor);

        StopContext stopContext = mock(StopContext.class);
        InOrder stopOrder = inOrder(lifecycle, captor);

        doReturn(true).when(lifecycle).isStarted();

        service.stop(stopContext);

        verifyNoInteractions(stopContext);
        stopOrder.verify(captor).accept(null);
        verifyNoMoreInteractions(captor);
        verifyNoMoreInteractions(dependency);
        verifyNoMoreInteractions(lifecycleProvider);
        stopOrder.verify(lifecycle).isStarted();
        stopOrder.verify(lifecycle).stop();
        stopOrder.verify(lifecycle).close();
        verifyNoMoreInteractions(lifecycle);
    }

    @Test
    public void async() throws StartException {
        Supplier<Object> factory = mock(Supplier.class);
        Function<Object, BlockingLifecycle> lifecycleProvider = mock(Function.class);
        BlockingLifecycle lifecycle = mock(BlockingLifecycle.class);
        Consumer<Object> captor = mock(Consumer.class);

        doCallRealMethod().when(captor).andThen(any());

        ServiceInstaller installer = ServiceInstaller.BlockingBuilder.of(factory, ServiceDependency.of(Runnable::run)).withLifecycle(lifecycleProvider).withCaptor(captor).build();

        verifyNoInteractions(factory);
        verify(captor, atMostOnce()).andThen(any());
        verifyNoMoreInteractions(captor);

        ServiceBuilder<?> builder = mock(ServiceBuilder.class);

        org.jboss.msc.Service service = verifyInstall(installer, builder);

        // Default mode should be PASSIVE, since service does not provide a value
        verify(builder).setInitialMode(ServiceController.Mode.PASSIVE);

        StartContext startContext = mock(StartContext.class);
        InOrder startOrder = inOrder(lifecycle, captor, startContext);
        Object value = new Object();

        doReturn(value).when(factory).get();
        doReturn(lifecycle).when(lifecycleProvider).apply(value);
        doReturn(true).when(lifecycle).isStopped();

        service.start(startContext);

        verify(factory, only()).get();
        verify(lifecycleProvider, only()).apply(value);
        startOrder.verify(lifecycle).isStopped();
        startOrder.verify(lifecycle).start();
        verifyNoMoreInteractions(lifecycle);
        startOrder.verify(captor).accept(value);
        verifyNoMoreInteractions(captor);
        verify(startContext).asynchronous();
        startOrder.verify(startContext).complete();
        verifyNoMoreInteractions(startContext);

        StopContext stopContext = mock(StopContext.class);
        InOrder stopOrder = inOrder(lifecycle, captor, stopContext);

        doReturn(true).when(lifecycle).isStarted();

        service.stop(stopContext);

        stopOrder.verify(captor).accept(null);
        verifyNoMoreInteractions(factory);
        verifyNoMoreInteractions(lifecycleProvider);
        stopOrder.verify(lifecycle).isStarted();
        stopOrder.verify(lifecycle).stop();
        verifyNoMoreInteractions(captor);
        verify(stopContext).asynchronous();
        stopOrder.verify(stopContext).complete();
        verifyNoMoreInteractions(stopContext);
    }

    @Test
    public void mapped() throws StartException {
        ServiceName name = ServiceName.of("foo", "bar");
        Supplier<Object> factory = mock(Supplier.class);
        Function<Object, BlockingLifecycle> lifecycleProvider = mock(Function.class);
        BlockingLifecycle lifecycle = mock(BlockingLifecycle.class);
        Consumer<Object> sourceCaptor = mock(Consumer.class);
        Consumer<Object> valueCaptor = mock(Consumer.class);
        Function<Object, Object> mapper = mock(Function.class);

        doCallRealMethod().when(sourceCaptor).andThen(any());
        doCallRealMethod().when(valueCaptor).andThen(any());
        doCallRealMethod().when(mapper).andThen(any());
        doCallRealMethod().when(mapper).compose(any());

        ServiceInstaller installer = ServiceInstaller.BlockingBuilder.of(factory).withLifecycle(lifecycleProvider).withCaptor(sourceCaptor).map(mapper).withCaptor(valueCaptor).provides(name).build();

        verifyNoInteractions(factory);
        verifyNoInteractions(lifecycleProvider);
        verifyNoInteractions(lifecycle);
        verify(sourceCaptor, atMostOnce()).andThen(any());
        verifyNoMoreInteractions(sourceCaptor);
        verify(valueCaptor, atMostOnce()).andThen(any());
        verifyNoMoreInteractions(valueCaptor);

        ServiceBuilder<?> builder = mock(ServiceBuilder.class);
        Consumer<Object> injector = mock(Consumer.class);

        doReturn(injector).when(builder).provides(new ServiceName[] { name });
        doCallRealMethod().when(injector).andThen(any());

        org.jboss.msc.Service service = verifyInstall(installer, builder);

        verify(builder).provides(name);
        // Default mode should be ON_DEMAND, since service provides a value
        verify(builder).setInitialMode(ServiceController.Mode.ON_DEMAND);

        StartContext startContext = mock(StartContext.class);
        InOrder startOrder = inOrder(lifecycle, sourceCaptor, valueCaptor);
        Object source = new Object();
        Object value = new Object();

        doReturn(source).when(factory).get();
        doReturn(lifecycle).when(lifecycleProvider).apply(source);
        doReturn(true).when(lifecycle).isStopped();
        doReturn(value).when(mapper).apply(source);

        service.start(startContext);

        verifyNoInteractions(startContext);
        // Verify operations on source value
        verify(factory, only()).get();
        verify(lifecycleProvider, only()).apply(source);
        // Verify lifecycle
        startOrder.verify(lifecycle).isStopped();
        startOrder.verify(lifecycle).start();
        verifyNoMoreInteractions(lifecycle);
        startOrder.verify(sourceCaptor).accept(source);
        verifyNoMoreInteractions(sourceCaptor);
        // Verify operations on mapped value
        verify(mapper).apply(source);
        verify(injector, atMostOnce()).andThen(any());
        verify(injector).accept(value);
        verifyNoMoreInteractions(injector);
        startOrder.verify(valueCaptor).accept(value);
        verifyNoMoreInteractions(valueCaptor);

        StopContext stopContext = mock(StopContext.class);
        InOrder stopOrder = inOrder(lifecycle, sourceCaptor, valueCaptor);

        doReturn(true).when(lifecycle).isStarted();

        service.stop(stopContext);

        verifyNoInteractions(stopContext);
        verifyNoMoreInteractions(factory);
        verifyNoMoreInteractions(lifecycleProvider);
        // Verify operations on source value
        stopOrder.verify(sourceCaptor).accept(null);
        // Verify operations on service value
        stopOrder.verify(valueCaptor).accept(null);
        verify(injector).accept(null);
        // Verify lifecycle
        stopOrder.verify(lifecycle).isStarted();
        stopOrder.verify(lifecycle).stop();
        stopOrder.verify(lifecycle).close();
        verifyNoMoreInteractions(lifecycle);
    }

    @Test
    public void nonBlocking() throws StartException {
        ServiceName name = ServiceName.of("foo", "bar");
        Supplier<CompletionStage<Object>> factory = mock(Supplier.class);
        Function<Object, NonBlockingLifecycle> lifecycleProvider = mock(Function.class);
        NonBlockingLifecycle lifecycle = mock(NonBlockingLifecycle.class);
        Consumer<Object> sourceCaptor = mock(Consumer.class);
        Consumer<Object> valueCaptor = mock(Consumer.class);
        Function<Object, Object> mapper = mock(Function.class);

        doCallRealMethod().when(sourceCaptor).andThen(any());
        doCallRealMethod().when(valueCaptor).andThen(any());
        doCallRealMethod().when(mapper).andThen(any());
        doCallRealMethod().when(mapper).compose(any());

        ServiceInstaller installer = ServiceInstaller.NonBlockingBuilder.of(factory).provides(name).withLifecycle(lifecycleProvider).withCaptor(sourceCaptor).map(mapper).withCaptor(valueCaptor).build();

        verifyNoInteractions(factory);
        verifyNoInteractions(lifecycleProvider);
        verifyNoInteractions(lifecycle);
        verify(sourceCaptor, atMostOnce()).andThen(any());
        verifyNoMoreInteractions(sourceCaptor);
        verify(valueCaptor, atMostOnce()).andThen(any());
        verifyNoMoreInteractions(valueCaptor);

        ServiceBuilder<?> builder = mock(ServiceBuilder.class);
        Consumer<Object> injector = mock(Consumer.class);

        doReturn(injector).when(builder).provides(new ServiceName[] { name });
        doCallRealMethod().when(injector).andThen(any());

        org.jboss.msc.Service service = verifyInstall(installer, builder);

        verify(builder).provides(name);
        // Default mode should be ON_DEMAND, since service provides a value
        verify(builder).setInitialMode(ServiceController.Mode.ON_DEMAND);

        StartContext startContext = mock(StartContext.class);
        Object source = new Object();
        Object value = new Object();

        doReturn(CompletableFuture.completedStage(source)).when(factory).get();
        doReturn(lifecycle).when(lifecycleProvider).apply(source);
        doReturn(true).when(lifecycle).isStopped();
        doReturn(CompletableFuture.completedStage(null)).when(lifecycle).start();
        doReturn(value).when(mapper).apply(source);

        service.start(startContext);

        verify(startContext).asynchronous();
        verify(startContext).complete();
        // Verify operations on source value
        verify(factory, only()).get();
        verify(lifecycleProvider, only()).apply(source);
        verify(lifecycle).isStopped();
        verify(lifecycle).start();
        verifyNoMoreInteractions(lifecycle);
        verify(sourceCaptor).accept(source);
        verifyNoMoreInteractions(sourceCaptor);
        verify(mapper).apply(source);
        // Verify operations on mapped value
        verify(injector, atMostOnce()).andThen(any());
        verify(injector).accept(value);
        verifyNoMoreInteractions(injector);
        verify(valueCaptor).accept(value);
        verifyNoMoreInteractions(valueCaptor);

        StopContext stopContext = mock(StopContext.class);

        doReturn(true).when(lifecycle).isStarted();
        doReturn(CompletableFuture.completedStage(null)).when(lifecycle).stop();
        doReturn(CompletableFuture.completedStage(null)).when(lifecycle).close();

        service.stop(stopContext);

        verify(stopContext).asynchronous();
        verify(stopContext).complete();
        verifyNoMoreInteractions(factory);
        verifyNoMoreInteractions(lifecycleProvider);
        // Verify operations on source value
        verify(sourceCaptor).accept(null);
        verifyNoMoreInteractions(sourceCaptor);
        verify(lifecycle).isStarted();
        verify(lifecycle).stop();
        verify(lifecycle).close();
        verifyNoMoreInteractions(lifecycle);
        // Verify operations on service value
        verify(valueCaptor).accept(null);
        verifyNoMoreInteractions(valueCaptor);
        verify(injector).accept(null);
        verifyNoMoreInteractions(injector);
    }

    @Test
    public void blockingLifecycle() throws StartException {
        BlockingLifecycle lifecycle = mock(BlockingLifecycle.class);

        ServiceInstaller installer = ServiceInstaller.BlockingLifecycleBuilder.of(Functions.constantSupplier(lifecycle)).build();

        verifyNoInteractions(lifecycle);

        ServiceBuilder<?> builder = mock(ServiceBuilder.class);

        org.jboss.msc.Service service = verifyInstall(installer, builder);

        // Default mode should be PASSIVE, since service does not provide a value
        verify(builder).setInitialMode(ServiceController.Mode.PASSIVE);

        StartContext startContext = mock(StartContext.class);
        InOrder startOrder = inOrder(lifecycle);

        doReturn(true).when(lifecycle).isStopped();

        service.start(startContext);

        verifyNoInteractions(startContext);
        startOrder.verify(lifecycle).isStopped();
        startOrder.verify(lifecycle).start();
        verifyNoMoreInteractions(lifecycle);

        StopContext stopContext = mock(StopContext.class);
        InOrder stopOrder = inOrder(lifecycle);

        doReturn(true).when(lifecycle).isStarted();

        service.stop(stopContext);

        verifyNoInteractions(stopContext);
        stopOrder.verify(lifecycle).isStarted();
        stopOrder.verify(lifecycle).stop();
        stopOrder.verify(lifecycle).close();
        verifyNoMoreInteractions(lifecycle);
    }

    @Test
    public void nonBlockingLifecycle() throws StartException {
        NonBlockingLifecycle lifecycle = mock(NonBlockingLifecycle.class);

        ServiceInstaller installer = ServiceInstaller.NonBlockingLifecycleBuilder.of(Functions.constantSupplier(CompletableFuture.completedStage(lifecycle))).build();

        verifyNoInteractions(lifecycle);

        ServiceBuilder<?> builder = mock(ServiceBuilder.class);

        org.jboss.msc.Service service = verifyInstall(installer, builder);

        // Default mode should be ON_DEMAND, since service provides no value
        verify(builder).setInitialMode(ServiceController.Mode.PASSIVE);

        StartContext startContext = mock(StartContext.class);
        InOrder startOrder = inOrder(lifecycle, startContext);

        doReturn(true).when(lifecycle).isStopped();
        doReturn(NonBlockingLifecycle.COMPLETED).when(lifecycle).start();

        service.start(startContext);

        startOrder.verify(lifecycle).isStopped();
        startOrder.verify(lifecycle).start();
        verifyNoMoreInteractions(lifecycle);
        startOrder.verify(startContext).complete();
        startOrder.verify(startContext).asynchronous();
        verifyNoMoreInteractions(startContext);

        StopContext stopContext = mock(StopContext.class);
        InOrder stopOrder = inOrder(lifecycle, stopContext);

        doReturn(true).when(lifecycle).isStarted();
        doReturn(NonBlockingLifecycle.COMPLETED).when(lifecycle).stop();
        doReturn(NonBlockingLifecycle.COMPLETED).when(lifecycle).close();

        service.stop(stopContext);

        stopOrder.verify(lifecycle).isStarted();
        stopOrder.verify(lifecycle).stop();
        stopOrder.verify(lifecycle).close();
        verifyNoMoreInteractions(lifecycle);
        stopOrder.verify(stopContext).complete();
        stopOrder.verify(stopContext).asynchronous();
        verifyNoMoreInteractions(stopContext);
    }

    private static org.jboss.msc.Service verifyInstall(ServiceInstaller installer, ServiceBuilder<?> builder) {

        ServiceTarget target = mock(ServiceTarget.class);
        ServiceController<?> controller = mock(ServiceController.class);
        ArgumentCaptor<org.jboss.msc.Service> serviceCaptor = ArgumentCaptor.forClass(org.jboss.msc.Service.class);

        doReturn(builder).when(target).addService();
        doReturn(builder).when(builder).setInitialMode(any());
        doReturn(builder).when(builder).setInstance(serviceCaptor.capture());
        doReturn(controller).when(builder).install();

        ServiceController<?> result = installer.install(target);

        verify(target).addService();
        verifyNoMoreInteractions(target);
        verify(builder).setInitialMode(any());
        verify(builder).setInstance(any());
        verifyNoMoreInteractions(target);
        verifyNoInteractions(controller);

        assertThat(result).isSameAs(controller);

        return serviceCaptor.getValue();
    }
}
