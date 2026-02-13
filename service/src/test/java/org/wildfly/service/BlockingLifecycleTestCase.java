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
        Object value = new Object();

        BlockingLifecycle lifecycle = BlockingLifecycle.compose(start, stop).apply(value);

        // Verify initial state
        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();

        verifyNoInteractions(start);
        verifyNoInteractions(stop);

        // Verify start
        lifecycle.start();

        verify(start, only()).accept(value);
        verifyNoInteractions(stop);

        assertThat(lifecycle.isStarted()).isTrue();
        assertThat(lifecycle.isStopped()).isFalse();

        lifecycle.start();

        // Verify no-op if already started
        verifyNoMoreInteractions(start);
        verifyNoInteractions(stop);

        assertThat(lifecycle.isStarted()).isTrue();
        assertThat(lifecycle.isStopped()).isFalse();

        // Verify stop
        lifecycle.stop();

        verifyNoMoreInteractions(start);
        verify(stop, only()).accept(value);

        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();

        lifecycle.stop();

        // Verify no-op if already stopped
        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);

        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();

        RuntimeException startException = new RuntimeException();

        // Verify failed start
        doThrow(startException).when(start).accept(value);

        assertThatRuntimeException().isThrownBy(() -> lifecycle.start()).isSameAs(startException);

        verify(start, times(2)).accept(value);

        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();

        // Verify failed start can be retried
        doNothing().when(start).accept(value);

        lifecycle.start();

        verify(start, times(3)).accept(value);
        verifyNoMoreInteractions(start);
        verifyNoMoreInteractions(stop);

        assertThat(lifecycle.isStarted()).isTrue();
        assertThat(lifecycle.isStopped()).isFalse();

        RuntimeException stopException = new RuntimeException();

        // Verify failed stop
        doThrow(stopException).when(stop).accept(value);

        assertThatRuntimeException().isThrownBy(() -> lifecycle.stop()).isSameAs(stopException);

        assertThat(lifecycle.isStarted()).isFalse();
        assertThat(lifecycle.isStopped()).isTrue();
    }
}
