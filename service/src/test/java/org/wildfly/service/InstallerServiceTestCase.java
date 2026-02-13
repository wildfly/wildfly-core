/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Unit test for {@link Service} implementations of {@link Installer}.
 * @author Paul Ferraro
 */
public class InstallerServiceTestCase {

    @Test
    public void blocking() throws StartException {
        Supplier<Object> provider = mock(Supplier.class);
        Function<Object, BlockingLifecycle> lifecycleProvider = mock(Function.class);
        BlockingLifecycle lifecycle = mock(BlockingLifecycle.class);
        Consumer<Object> captor = mock(Consumer.class);
        Object value = "value";

        Service service = new Installer.BlockingValueService<>(provider, lifecycleProvider, captor);

        verifyNoInteractions(provider);
        verifyNoInteractions(lifecycleProvider);
        verifyNoInteractions(captor);

        StartContext startContext = mock(StartContext.class);
        InOrder startOrder = inOrder(lifecycle, captor);

        doReturn(value).when(provider).get();
        doReturn(lifecycle).when(lifecycleProvider).apply(value);
        doReturn(true).when(lifecycle).isStopped();
        doReturn(true).when(lifecycle).isStarted();

        // Validate successful start
        service.start(startContext);

        verify(provider, only()).get();
        verify(lifecycleProvider, only()).apply(value);
        verify(lifecycle).isStopped();
        // Start should be called before captor
        startOrder.verify(lifecycle).start();
        startOrder.verify(captor).accept(value);
        verifyNoMoreInteractions(lifecycle);
        verifyNoMoreInteractions(captor);
        verifyNoInteractions(startContext);

        StopContext stopContext = mock(StopContext.class);
        InOrder stopOrder = inOrder(lifecycle, captor);

        // Validate stop
        service.stop(stopContext);

        verify(lifecycle).isStarted();
        // Captor should be called before stop
        stopOrder.verify(captor).accept(null);
        stopOrder.verify(lifecycle).stop();
        verifyNoMoreInteractions(provider);
        verifyNoMoreInteractions(lifecycleProvider);
        verifyNoMoreInteractions(captor);
        verifyNoInteractions(stopContext);

        // Validate start failure
        RuntimeException startException = new RuntimeException();

        doThrow(startException).when(lifecycle).start();

        assertThatExceptionOfType(StartException.class).isThrownBy(() -> service.start(startContext)).havingCause().isSameAs(startException);

        // Validate provider failure
        RuntimeException providerException = new RuntimeException();

        doThrow(providerException).when(provider).get();

        assertThatExceptionOfType(StartException.class).isThrownBy(() -> service.start(startContext)).havingCause().isSameAs(providerException);
    }

    @Test
    public void nonBlocking() throws StartException {
        Supplier<CompletionStage<Object>> provider = mock(Supplier.class);
        Function<Object, NonBlockingLifecycle> lifecycleProvider = mock(Function.class);
        NonBlockingLifecycle lifecycle = mock(NonBlockingLifecycle.class);
        Consumer<Object> captor = mock(Consumer.class);
        Object value = "value";
        CompletableFuture<Object> providerFuture = new CompletableFuture<>();
        CompletableFuture<Void> startFuture = new CompletableFuture<>();
        CompletableFuture<Void> stopFuture = new CompletableFuture<>();

        Service service = new Installer.NonBlockingValueService<>(provider, lifecycleProvider, captor);

        verifyNoInteractions(provider);
        verifyNoInteractions(lifecycleProvider);
        verifyNoInteractions(captor);

        doReturn(providerFuture).when(provider).get();
        doReturn(lifecycle).when(lifecycleProvider).apply(value);
        doReturn(true).when(lifecycle).isStopped();
        doReturn(true).when(lifecycle).isStarted();
        doReturn(startFuture).when(lifecycle).start();
        doReturn(stopFuture).when(lifecycle).stop();

        StartContext startContext = mock(StartContext.class);

        service.start(startContext);

        verify(provider, only()).get();
        verify(startContext, only()).asynchronous();
        // Verify not yet complete
        verifyNoInteractions(lifecycle);
        verifyNoInteractions(captor);

        providerFuture.complete(value);

        // Verify not yet complete
        verifyNoMoreInteractions(startContext);
        verifyNoMoreInteractions(provider);
        verify(lifecycleProvider, only()).apply(value);
        verify(lifecycle).isStopped();
        verify(lifecycle).start();
        verifyNoMoreInteractions(lifecycle);
        verifyNoInteractions(captor);

        startFuture.complete(null);

        // Verify completion
        verify(startContext).complete();
        verifyNoMoreInteractions(startContext);
        verifyNoMoreInteractions(provider);
        verifyNoMoreInteractions(lifecycleProvider);
        verifyNoMoreInteractions(lifecycle);
        verify(captor, only()).accept(value);

        StopContext stopContext = mock(StopContext.class);
        InOrder stopOrder = inOrder(lifecycle, captor);

        service.stop(stopContext);

        verify(stopContext).asynchronous();
        verify(lifecycle).isStarted();
        // Verify not yet complete
        verifyNoMoreInteractions(stopContext);
        verifyNoMoreInteractions(provider);
        verifyNoMoreInteractions(lifecycleProvider);
        // Captor should be called before stop
        stopOrder.verify(captor).accept(null);
        stopOrder.verify(lifecycle).stop();
        verifyNoMoreInteractions(lifecycle);
        verifyNoMoreInteractions(captor);

        stopFuture.complete(null);

        // Verify complete
        verify(stopContext).complete();
        verifyNoMoreInteractions(stopContext);
        verifyNoMoreInteractions(provider);
        verifyNoMoreInteractions(lifecycleProvider);
        verifyNoMoreInteractions(lifecycle);
        verifyNoMoreInteractions(captor);

        // Validate provider failure
        RuntimeException providerException = new RuntimeException();
        ArgumentCaptor<StartException> capturedException = ArgumentCaptor.captor();

        doReturn(CompletableFuture.failedStage(providerException)).when(provider).get();

        reset(startContext);

        service.start(startContext);

        verify(startContext).asynchronous();
        verify(startContext).failed(capturedException.capture());
        verifyNoMoreInteractions(startContext);

        assertThat(capturedException.getValue()).cause().isInstanceOf(CompletionException.class).cause().isSameAs(providerException);

        // Validate start failure
        RuntimeException startException = new RuntimeException();
        capturedException = ArgumentCaptor.captor();

        doReturn(CompletableFuture.completedStage(value)).when(provider).get();
        doReturn(CompletableFuture.failedStage(startException)).when(lifecycle).start();

        reset(startContext);

        service.start(startContext);

        verify(startContext).asynchronous();
        verify(startContext).failed(capturedException.capture());
        verifyNoMoreInteractions(startContext);

        assertThat(capturedException.getValue()).cause().isInstanceOf(CompletionException.class).cause().isSameAs(startException);
    }
}
