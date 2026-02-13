/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.wildfly.common.function.Functions;

/**
 * Encapsulates blocking lifecycle (i.e. start/stop) behaviour.
 * @author Paul Ferraro
 */
public interface BlockingLifecycle extends Lifecycle {
    /**
     * Initiates start behaviour, blocking until complete.
     * {@link #isStarted()} should return true after this method returns successfully.
     */
    void start();

    /**
     * Initiates stop behaviour, blocking until complete.
     * {@link #isStopped()} should return true after this method returns, regardless of success.
     */
    void stop();

    /**
     * A blocking lifecycle that performs no actions on start/stop.
     */
    BlockingLifecycle NONE = new BlockingLifecycle() {
        @Override
        public boolean isStarted() {
            return false;
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    Function<AutoCloseable, BlockingLifecycle> AUTO_CLOSE_PROVIDER = compose(Functions.discardingConsumer(), Functions.closingConsumer());

    /**
     * Returns a blocking lifecycle provider of an auto-closeable object.
     * @param <T> the auto-closeable object type
     * @return a blocking lifecycle provider of an auto-closeable object.
     */
    @SuppressWarnings("unchecked")
    static <T extends AutoCloseable> Function<T, BlockingLifecycle> close() {
        return (Function<T, BlockingLifecycle>) AUTO_CLOSE_PROVIDER;
    }

    /**
     * Composes a blocking lifecycle provider from the specified start/stop behaviour.
     * @param <T> the controlled object type
     * @param start a consumer encapsulating start behaviour
     * @param stop a consumer encapsulating stop behaviour
     * @return
     */
    static <T> Function<T, BlockingLifecycle> compose(Consumer<? super T> start, Consumer<? super T> stop) {
        return new Function<>() {
            @Override
            public BlockingLifecycle apply(T value) {
                return new BlockingLifecycle() {
                    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

                    @Override
                    public boolean isStarted() {
                        return this.state.get() == State.STARTED;
                    }

                    @Override
                    public boolean isStopped() {
                        return this.state.get() == State.STOPPED;
                    }

                    @Override
                    public void start() {
                        if (this.state.compareAndSet(State.STOPPED, State.STARTING)) {
                            try {
                                start.accept(value);
                                this.state.set(State.STARTED);
                            } catch (RuntimeException | Error e) {
                                // If start failed, reset status
                                this.state.set(State.STOPPED);
                                throw e;
                            }
                        }
                    }

                    @Override
                    public void stop() {
                        if (this.state.compareAndSet(State.STARTED, State.STOPPING)) {
                            try {
                                stop.accept(value);
                            } finally {
                                // Set state to stopped, even if exceptional
                                this.state.set(State.STOPPED);
                            }
                        }
                    }
                };
            }
        };
    }
}
