/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.wildfly.common.function.Functions;

/**
 * Encapsulates blocking lifecycle (i.e. start/stop) behaviour.
 * Implementors should retain symmetry between {@link #start()} and {@link #stop()}, while {@link #close()} should encapsulate any terminal lifecycle behaviour..
 * @author Paul Ferraro
 */
public interface BlockingLifecycle extends Lifecycle, AutoCloseable {
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

    @Override
    default void close() {
        // Do nothing
    }

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

    Function<AutoCloseable, BlockingLifecycle> AUTO_CLOSE_PROVIDER = compose(Functions.discardingConsumer(), Functions.discardingConsumer(), Functions.closingConsumer());

    /**
     * Returns a blocking lifecycle provider of an auto-closeable object.
     * @param <T> the auto-closeable object type
     * @return a blocking lifecycle provider of an auto-closeable object.
     */
    @SuppressWarnings("unchecked")
    static <T extends AutoCloseable> Function<T, BlockingLifecycle> autoClose() {
        return (Function<T, BlockingLifecycle>) AUTO_CLOSE_PROVIDER;
    }

    /**
     * Composes a blocking lifecycle provider with the specified closure.
     * @param <T> the controlled object type
     * @param close a consumer encapsulating closure
     * @return a blocking lifecycle provider
     */
    static <T> Function<T, BlockingLifecycle> compose(Consumer<? super T> close) {
        return compose(DiscardingConsumer.of(), DiscardingConsumer.of(), close);
    }

    /**
     * Composes a blocking lifecycle provider from the specified start/stop behaviour.
     * @param <T> the controlled object type
     * @param start a consumer encapsulating start behaviour
     * @param stop a consumer encapsulating stop behaviour
     * @return a blocking lifecycle provider
     */
    static <T> Function<T, BlockingLifecycle> compose(Consumer<? super T> start, Consumer<? super T> stop) {
        return compose(start, stop, DiscardingConsumer.of());
    }

    /**
     * Composes a blocking lifecycle provider from the specified start/stop behaviour.
     * @param <T> the controlled object type
     * @param start a consumer encapsulating start behaviour
     * @param stop a consumer encapsulating stop behaviour
     * @param close a consumer encapsulating closure
     * @return a blocking lifecycle provider
     */
    static <T> Function<T, BlockingLifecycle> compose(Consumer<? super T> start, Consumer<? super T> stop, Consumer<? super T> close) {
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
                    public boolean isClosed() {
                        return this.state.get() == State.CLOSED;
                    }

                    @Override
                    public void start() {
                        if (this.state.compareAndSet(State.STOPPED, State.STARTING)) {
                            State targetState = State.STOPPED;
                            try {
                                start.accept(value);
                                targetState = State.STARTED;
                            } finally {
                                this.state.compareAndSet(State.STARTING, targetState);
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
                                this.state.compareAndSet(State.STOPPING, State.STOPPED);
                            }
                        }
                    }

                    @Override
                    public void close() {
                        // Terminal state
                        if (this.state.getAndSet(State.CLOSED) != State.CLOSED) {
                            close.accept(value);
                        }
                    }
                };
            }
        };
    }

    /**
     * Composes a composite lifecycle provider from multiple lifecycle providers.
     * @param <T> the controlled object type
     * @param lifecycleProviders a list of lifecycle providers
     * @return a composite lifecycle provider
     */
    static <T> Function<T, BlockingLifecycle> combine(List<Function<? super T, ? extends BlockingLifecycle>> lifecycleProviders) {
        return new Function<>() {
            @Override
            public BlockingLifecycle apply(T value) {
                if (lifecycleProviders.isEmpty()) return BlockingLifecycle.NONE;
                List<BlockingLifecycle> lifecycles = new ArrayList<>(lifecycleProviders.size());
                for (Function<? super T, ? extends BlockingLifecycle> lifecycleProvider : lifecycleProviders) {
                    lifecycles.add(lifecycleProvider.apply(value));
                }
                return new BlockingLifecycle() {
                    @Override
                    public boolean isStarted() {
                        return lifecycles.stream().allMatch(Lifecycle::isStarted);
                    }

                    @Override
                    public boolean isStopped() {
                        return lifecycles.stream().allMatch(Lifecycle::isStopped);
                    }

                    @Override
                    public boolean isClosed() {
                        return lifecycles.stream().allMatch(Lifecycle::isClosed);
                    }

                    @Override
                    public void start() {
                        for (BlockingLifecycle lifecycle : lifecycles) {
                            if (lifecycle.isStopped()) {
                                lifecycle.start();
                            }
                        }
                    }

                    @Override
                    public void stop() {
                        // Stop in reverse order
                        ListIterator<BlockingLifecycle> iterator = lifecycles.listIterator(lifecycles.size());
                        while (iterator.hasPrevious()) {
                            BlockingLifecycle lifecycle = iterator.previous();
                            if (lifecycle.isStarted()) {
                                lifecycle.stop();
                            }
                        }
                    }

                    @Override
                    public void close() {
                        // Close in reverse order
                        ListIterator<BlockingLifecycle> iterator = lifecycles.listIterator(lifecycles.size());
                        while (iterator.hasPrevious()) {
                            BlockingLifecycle lifecycle = iterator.previous();
                            if (!lifecycle.isClosed()) {
                                lifecycle.close();
                            }
                        }
                    }
                };
            }
        };
    }
}
