/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.junit.Test;

/**
 * Unit test for a composed {@link BlockingLifecycle}.
 * @author Paul Ferraro
 */
public class BlockingLifecycleTestCase {

    @Test
    public void nonBlocking() {
        NonBlockingLifecycle nonBlocking = mock(NonBlockingLifecycle.class);
        RuntimeException startException = new RuntimeException();
        RuntimeException stopException = new RuntimeException();
        RuntimeException closeException = new RuntimeException();

        try (BlockingLifecycle lifecycle = BlockingLifecycle.join(nonBlocking)) {

            doReturn(true, false).when(nonBlocking).isStarted();
            doReturn(true, false).when(nonBlocking).isStopped();
            doReturn(true, false).when(nonBlocking).isClosed();

            assertThat(lifecycle.isStarted()).isTrue();
            assertThat(lifecycle.isStarted()).isFalse();

            verify(nonBlocking, times(2)).isStarted();

            assertThat(lifecycle.isStopped()).isTrue();
            assertThat(lifecycle.isStopped()).isFalse();

            verify(nonBlocking, times(2)).isStopped();

            assertThat(lifecycle.isClosed()).isTrue();
            assertThat(lifecycle.isClosed()).isFalse();

            verify(nonBlocking, times(2)).isClosed();

            doReturn(CompletableFuture.failedStage(startException), CompletableFuture.completedStage(null)).when(nonBlocking).start();
            doReturn(CompletableFuture.failedStage(stopException), CompletableFuture.completedStage(null)).when(nonBlocking).stop();
            doReturn(CompletableFuture.failedStage(closeException), CompletableFuture.completedStage(null)).when(nonBlocking).close();

            assertThatExceptionOfType(CompletionException.class).isThrownBy(() -> lifecycle.start()).havingCause().isSameAs(startException);
            assertThatNoException().isThrownBy(() -> lifecycle.start());

            verify(nonBlocking, times(2)).start();

            assertThatExceptionOfType(CompletionException.class).isThrownBy(() -> lifecycle.stop()).havingCause().isSameAs(stopException);
            assertThatNoException().isThrownBy(() -> lifecycle.stop());

            verify(nonBlocking, times(2)).stop();

            assertThatExceptionOfType(CompletionException.class).isThrownBy(() -> lifecycle.close()).havingCause().isSameAs(closeException);
        }

        verify(nonBlocking, times(2)).close();
    }

    @Test
    public void compose() {
        Consumer<Object> start = mock(Consumer.class);
        Consumer<Object> stop = mock(Consumer.class);
        Consumer<Object> close = mock(Consumer.class);
        Object value = new Object();

        try (BlockingLifecycle lifecycle = BlockingLifecycle.compose(start, stop, close).apply(value)) {

            // Verify initial state
            assertThat(lifecycle.isStarted()).isFalse();
            assertThat(lifecycle.isStopped()).isTrue();
            assertThat(lifecycle.isClosed()).isFalse();

            verifyNoInteractions(start);
            verifyNoInteractions(stop);
            verifyNoInteractions(close);

            // Verify start
            lifecycle.start();

            verify(start, only()).accept(value);
            verifyNoInteractions(stop);
            verifyNoInteractions(close);

            assertThat(lifecycle.isStarted()).isTrue();
            assertThat(lifecycle.isStopped()).isFalse();
            assertThat(lifecycle.isClosed()).isFalse();

            // Verify no-op if already started
            lifecycle.start();

            verifyNoMoreInteractions(start);
            verifyNoInteractions(stop);
            verifyNoInteractions(close);

            assertThat(lifecycle.isStarted()).isTrue();
            assertThat(lifecycle.isStopped()).isFalse();
            assertThat(lifecycle.isClosed()).isFalse();

            // Verify stop
            lifecycle.stop();

            verifyNoMoreInteractions(start);
            verify(stop, only()).accept(value);
            verifyNoInteractions(close);

            assertThat(lifecycle.isStarted()).isFalse();
            assertThat(lifecycle.isStopped()).isTrue();
            assertThat(lifecycle.isClosed()).isFalse();

            // Verify no-op if already stopped
            lifecycle.stop();

            verifyNoMoreInteractions(start);
            verifyNoMoreInteractions(stop);
            verifyNoInteractions(close);

            assertThat(lifecycle.isStarted()).isFalse();
            assertThat(lifecycle.isStopped()).isTrue();
            assertThat(lifecycle.isClosed()).isFalse();

            RuntimeException startException = new RuntimeException();

            // Verify failed start
            doThrow(startException).when(start).accept(value);

            assertThatRuntimeException().isThrownBy(() -> lifecycle.start()).isSameAs(startException);

            verify(start, times(2)).accept(value);
            verifyNoMoreInteractions(start);
            verifyNoMoreInteractions(stop);
            verifyNoInteractions(close);

            assertThat(lifecycle.isStarted()).isFalse();
            assertThat(lifecycle.isStopped()).isTrue();
            assertThat(lifecycle.isClosed()).isFalse();

            // Verify failed start can be retried
            doNothing().when(start).accept(value);

            lifecycle.start();

            verify(start, times(3)).accept(value);
            verifyNoMoreInteractions(start);
            verifyNoMoreInteractions(stop);
            verifyNoInteractions(close);

            assertThat(lifecycle.isStarted()).isTrue();
            assertThat(lifecycle.isStopped()).isFalse();
            assertThat(lifecycle.isClosed()).isFalse();

            RuntimeException stopException = new RuntimeException();

            // Verify failed stop
            doThrow(stopException).when(stop).accept(value);

            assertThatRuntimeException().isThrownBy(() -> lifecycle.stop()).isSameAs(stopException);

            verifyNoMoreInteractions(start);
            verify(stop, times(2)).accept(value);
            verifyNoMoreInteractions(stop);
            verifyNoInteractions(close);

            assertThat(lifecycle.isStarted()).isFalse();
            assertThat(lifecycle.isStopped()).isTrue();
            assertThat(lifecycle.isClosed()).isFalse();

            // Verify close
            lifecycle.close();

            verify(close).accept(value);
            verifyNoMoreInteractions(start);
            verifyNoMoreInteractions(stop);

            assertThat(lifecycle.isStarted()).isFalse();
            assertThat(lifecycle.isStopped()).isFalse();
            assertThat(lifecycle.isClosed()).isTrue();
        }
        // Verify no-op if already closed
        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verifyNoMoreInteractions(close);
    }
}
