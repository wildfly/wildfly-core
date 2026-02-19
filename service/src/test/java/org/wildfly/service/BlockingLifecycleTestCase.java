/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.function.Consumer;

import org.junit.Test;

/**
 * Unit test for a composed {@link BlockingLifecycle}.
 * @author Paul Ferraro
 */
public class BlockingLifecycleTestCase {

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
