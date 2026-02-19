/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.junit.Test;

/**
 * Unit test for a composed {@link NonBlockingLifecycle}.
 * @author Paul Ferraro
 */
public class NonBlockingLifecycleTestCase {

    @Test
    public void compose() {
        Function<Object, CompletionStage<Void>> start = mock(Function.class);
        Function<Object, CompletionStage<Void>> stop = mock(Function.class);
        Function<Object, CompletionStage<Void>> close = mock(Function.class);
        Object value = new Object();

        NonBlockingLifecycle lifecycle = NonBlockingLifecycle.compose(start, stop, close).apply(value);

        // Verify initial state
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();
        assertThat(lifecycle.isClosed()).isFalse();

        verifyNoInteractions(start);
        verifyNoInteractions(stop);
        verifyNoInteractions(close);

        CompletableFuture<Void> startFuture = new CompletableFuture<>();

        doReturn(startFuture).when(start).apply(value);

        // Verify start initiated
        CompletionStage<Void> startStage = lifecycle.start();

        verify(start, only()).apply(value);
        verifyNoInteractions(stop);
        verifyNoInteractions(close);

        assertThat(startStage).isNotCompleted();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify that we do not start twice if starting
        assertThat(lifecycle.start()).isCompleted();

        verifyNoMoreInteractions(start);
        verifyNoInteractions(stop);
        verifyNoInteractions(close);

        assertThat(startStage).isNotCompleted();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify start completion
        startFuture.complete(null);

        assertThat(startStage).isCompleted();
        assertThat(lifecycle.isStarted()).isTrue();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify redundant start
        assertThat(lifecycle.start()).isCompleted();

        verifyNoMoreInteractions(start);
        verifyNoInteractions(stop);
        verifyNoInteractions(close);

        assertThat(lifecycle.isStarted()).isTrue();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        CompletableFuture<Void> stopFuture = new CompletableFuture<>();

        doReturn(stopFuture).when(stop).apply(value);

        // Verify stop initiated
        CompletionStage<Void> stopStage = lifecycle.stop();

        verifyNoMoreInteractions(start);
        verify(stop, only()).apply(value);
        verifyNoInteractions(close);

        assertThat(stopStage).isNotCompleted();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify stop not initiated twice if already stopping
        assertThat(lifecycle.stop()).isCompleted();

        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verifyNoInteractions(close);

        assertThat(stopStage).isNotCompleted();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify start completion
        stopFuture.complete(null);

        assertThat(stopStage).isCompleted();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify redundant start
        assertThat(lifecycle.stop()).isCompleted();

        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verifyNoInteractions(close);

        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();
        assertThat(lifecycle.isClosed()).isFalse();

        RuntimeException startException = new RuntimeException();
        startFuture = new CompletableFuture<>();

        doReturn(startFuture).when(start).apply(value);

        startStage = lifecycle.start();

        verify(start, times(2)).apply(value);
        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verifyNoInteractions(close);

        assertThat(startStage).isNotCompleted();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        startFuture.completeExceptionally(startException);

        assertThat(startStage).isCompletedExceptionally();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify failed start can be retried
        doReturn(CompletableFuture.completedStage(null)).when(start).apply(value);

        assertThat(lifecycle.start()).isCompleted();

        verify(start, times(3)).apply(value);
        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verifyNoInteractions(close);

        assertThat(lifecycle.isStarted()).isTrue();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        RuntimeException stopException = new RuntimeException();
        stopFuture = new CompletableFuture<>();

        doReturn(stopFuture).when(stop).apply(value);

        stopStage = lifecycle.stop();

        verify(stop, times(2)).apply(value);
        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verifyNoInteractions(close);

        assertThat(stopStage).isNotCompleted();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify failed stop reports as stopped
        stopFuture.completeExceptionally(stopException);

        assertThat(stopStage).isCompletedExceptionally();
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();
        assertThat(lifecycle.isClosed()).isFalse();

        // Verify close
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();

        doReturn(closeFuture).when(close).apply(value);

        CompletionStage<Void> closeStage = lifecycle.close();

        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verify(close, only()).apply(value);

        assertThat(closeStage).isNotCompleted();
        // Verify state is closed immediately
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isFalse();
        assertThat(lifecycle.isClosed()).isTrue();

        closeFuture.complete(null);

        assertThat(closeStage).isCompleted();

        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verifyNoMoreInteractions(close);

        // Verify duplicate close is a no-op
        assertThat(lifecycle.close()).isCompleted();

        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);
        verifyNoMoreInteractions(close);
    }
}
