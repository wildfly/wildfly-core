/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.wildfly.common.function.Functions;
import org.wildfly.service.AsyncServiceBuilder.Async;

/**
 * Unit test for {@link AsyncServiceBuilder}.
 * @author Paul Ferraro
 */
public class AsyncServiceBuilderTestCase {
    @Test
    public void test() throws StartException {
        ServiceBuilder<Void> builder = mock(ServiceBuilder.class);
        Executor executor = mock(Executor.class);

        ServiceBuilder<Void> subject = new AsyncServiceBuilder<>(builder, Functions.constantSupplier(executor));

        Consumer<Object> injector = mock(Consumer.class);
        ServiceName name = ServiceName.JBOSS;

        doReturn(injector).when(builder).provides(name);

        Assert.assertSame(injector, subject.provides(name));

        Supplier<Object> dependency = mock(Supplier.class);
        ServiceName dependencyName = ServiceName.JBOSS.append("dependency");

        doReturn(dependency).when(builder).requires(dependencyName);

        Assert.assertSame(dependency, subject.requires(dependencyName));

        ServiceController.Mode mode = ServiceController.Mode.NEVER;

        doReturn(builder).when(builder).setInitialMode(mode);

        Assert.assertSame(subject, subject.setInitialMode(mode));

        LifecycleListener listener = mock(LifecycleListener.class);

        doReturn(builder).when(builder).addListener(listener);

        Assert.assertSame(subject, subject.addListener(listener));

        // Validate start/stop
        Service service = mock(Service.class);
        ArgumentCaptor<Service> capturedService = ArgumentCaptor.forClass(Service.class);

        doReturn(builder).when(builder).setInstance(capturedService.capture());

        Assert.assertSame(subject, subject.setInstance(service));

        Service asyncService = capturedService.getValue();

        testStart(asyncService, service, executor);

        reset(service, executor);

        testStop(asyncService, service, executor);
    }

    @Test
    public void testSyncStart() throws StartException {
        ServiceBuilder<Void> builder = mock(ServiceBuilder.class);
        Executor executor = mock(Executor.class);

        ServiceBuilder<Void> subject = new AsyncServiceBuilder<>(builder, Functions.constantSupplier(executor), Async.STOP_ONLY);

        Service service = mock(Service.class);
        ArgumentCaptor<Service> capturedService = ArgumentCaptor.forClass(Service.class);

        doReturn(builder).when(builder).setInstance(capturedService.capture());

        Assert.assertSame(subject, subject.setInstance(service));

        Service asyncService = capturedService.getValue();

        testSyncStart(asyncService, service, executor);

        reset(service, executor);

        testStop(asyncService, service, executor);
    }

    @Test
    public void testSyncStop() throws StartException {
        ServiceBuilder<Void> builder = mock(ServiceBuilder.class);
        Executor executor = mock(Executor.class);

        ServiceBuilder<Void> subject = new AsyncServiceBuilder<>(builder, Functions.constantSupplier(executor), Async.START_ONLY);

        Service service = mock(Service.class);
        ArgumentCaptor<Service> capturedService = ArgumentCaptor.forClass(Service.class);

        doReturn(builder).when(builder).setInstance(capturedService.capture());

        Assert.assertSame(subject, subject.setInstance(service));

        Service asyncService = capturedService.getValue();

        testStart(asyncService, service, executor);

        reset(service, executor);

        testSyncStop(asyncService, service, executor);
    }

    private static void testStart(Service subject, Service service, Executor executor) throws StartException {
        StartContext context = mock(StartContext.class);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        subject.start(context);

        verifyNoMoreInteractions(service);
        verify(context).asynchronous();
        verify(executor).execute(task.capture());
        verify(context, never()).complete();

        task.getValue().run();

        verifySyncStart(context, service, executor);
        verify(context).complete();

        reset(context, service, executor);
        // Validate rejected execution
        doThrow(RejectedExecutionException.class).when(executor).execute(any());

        subject.start(context);

        verify(context).asynchronous();
        verify(executor).execute(any());
        verifySyncStart(context, service, executor);
        verify(context).complete();
    }

    private static void testSyncStart(Service subject, Service service, Executor executor) throws StartException {
        StartContext context = mock(StartContext.class);

        subject.start(context);

        verifySyncStart(context, service, executor);
        verify(context, never()).asynchronous();
    }

    private static void verifySyncStart(StartContext context, Service service, Executor executor) throws StartException {
        verify(service).start(context);
        verifyNoMoreInteractions(service, executor);
    }

    private static void testStop(Service subject, Service service, Executor executor) {
        StopContext context = mock(StopContext.class);
        subject.stop(context);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        verifyNoMoreInteractions(service);
        verify(context).asynchronous();
        verify(executor).execute(task.capture());
        verify(context, never()).complete();

        task.getValue().run();

        verifySyncStop(context, service, executor);
        verify(context).complete();

        reset(context, service, executor);
        // Validate rejected execution
        doThrow(RejectedExecutionException.class).when(executor).execute(any());

        subject.stop(context);

        verify(context).asynchronous();
        verify(executor).execute(any());
        verifySyncStop(context, service, executor);
        verify(context).complete();
    }

    private static void testSyncStop(Service subject, Service service, Executor executor) {
        StopContext context = mock(StopContext.class);

        subject.stop(context);

        verifySyncStop(context, service, executor);
        verify(context, never()).asynchronous();
    }

    private static void verifySyncStop(StopContext context, Service service, Executor executor) {
        verify(service).stop(context);
        verifyNoMoreInteractions(service, executor);
    }
}
