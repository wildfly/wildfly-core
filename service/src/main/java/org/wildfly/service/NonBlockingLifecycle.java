/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Encapsulates non-blocking lifecycle (i.e. start/stop) behaviour.
 * @author Paul Ferraro
 */
public interface NonBlockingLifecycle extends Lifecycle {
    CompletionStage<Void> COMPLETED = CompletableFuture.completedStage(null);

    /**
     * Initiates start behaviour.
     * {@link #isStarted()} should return true after the returned stage completes successfully.
     * @return a stage that completes when started.
     */
    CompletionStage<Void> start();

    /**
     * Initiates start behaviour.
     * {@link #isStarted()} should return true after the returned stage completes, either successfully or exceptionally.
     * @return a stage that completes when stopped.
     */
    CompletionStage<Void> stop();

    /**
     * Initiates close behaviour.
     * {@link #isClosed()} should return true after the returned stage completes, either successfully or exceptionally.
     * @return a stage that completes when closed.
     */
    default CompletionStage<Void> close() {
        return COMPLETED;
    }

    /**
     * A non-blocking lifecycle that performs no actions on start/stop.
     */
    NonBlockingLifecycle NONE = new NonBlockingLifecycle() {
        @Override
        public boolean isStarted() {
            return false;
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public CompletionStage<Void> start() {
            return COMPLETED;
        }

        @Override
        public CompletionStage<Void> stop() {
            return COMPLETED;
        }
    };

    /**
     * Returns a non-blocking lifecycle that asynchronously invokes blocking lifecycle operations via the specified executor.
     * @param lifecycle a blocking lifecycle
     * @param executor an executor for asynchronous execution
     * @return a non-blocking lifecycle that asynchronously invokes blocking lifecycle operations via the specified executor.
     */
    static NonBlockingLifecycle async(BlockingLifecycle lifecycle, Executor executor) {
        return new NonBlockingLifecycle() {
            @Override
            public boolean isStarted() {
                return lifecycle.isStarted();
            }

            @Override
            public boolean isStopped() {
                return lifecycle.isStopped();
            }

            @Override
            public boolean isClosed() {
                return lifecycle.isClosed();
            }

            @Override
            public CompletionStage<Void> start() {
                try {
                    return CompletableFuture.runAsync(lifecycle::start, executor);
                } catch (RejectedExecutionException e) {
                    // Resort to work-stealing via common fork-join pool, if necessary
                    return CompletableFuture.runAsync(lifecycle::start);
                }
            }

            @Override
            public CompletionStage<Void> stop() {
                try {
                    return CompletableFuture.runAsync(lifecycle::stop, executor);
                } catch (RejectedExecutionException e) {
                    // Resort to work-stealing via common fork-join pool, if necessary
                    return CompletableFuture.runAsync(lifecycle::stop);
                }
            }

            @Override
            public CompletionStage<Void> close() {
                try {
                    return CompletableFuture.runAsync(lifecycle::close, executor);
                } catch (RejectedExecutionException e) {
                    // Resort to work-stealing via common fork-join pool, if necessary
                    return CompletableFuture.runAsync(lifecycle::close);
                }
            }
        };
    }

    /**
     * Composes a non-blocking lifecycle provider from the specified blocking lifecycle provider and executor.
     * @param <T> the operating type
     * @param lifecycle a blocking lifecycle
     * @param executor an executor provider
     * @return a non-blocking lifecycle provider from the specified blocking lifecycle provider and executor.
     */
    static <T> Function<T, NonBlockingLifecycle> composeAsync(Function<? super T, ? extends BlockingLifecycle> lifecycle, Supplier<Executor> executor) {
        return new Function<>() {
            @Override
            public NonBlockingLifecycle apply(T value) {
                return async(lifecycle.apply(value), executor.get());
            }
        };
    }

    /**
     * Composes a non-blocking lifecycle with the specified start/stop behaviour.
     * @param <T> the operating type
     * @param close a provider of non-blocking close behaviour
     * @return a non-blocking lifecycle with the specified start/stop behaviour.
     */
    static <T> Function<T, NonBlockingLifecycle> compose(Function<T, CompletionStage<Void>> close) {
        return compose(DiscardingFunction.complete(), DiscardingFunction.complete(), close);
    }

    /**
     * Composes a non-blocking lifecycle with the specified start/stop behaviour.
     * @param <T> the operating type
     * @param start a provider of non-blocking start behaviour
     * @param stop a provider of non-blocking stop behaviour
     * @return a non-blocking lifecycle with the specified start/stop behaviour.
     */
    static <T> Function<T, NonBlockingLifecycle> compose(Function<T, CompletionStage<Void>> start, Function<T, CompletionStage<Void>> stop) {
        return compose(start, stop, DiscardingFunction.complete());
    }

    /**
     * Composes a non-blocking lifecycle with the specified start/stop behaviour.
     * @param <T> the operating type
     * @param start a provider of non-blocking start behaviour
     * @param stop a provider of non-blocking stop behaviour
     * @param close a provider of non-blocking close behaviour
     * @return a non-blocking lifecycle with the specified start/stop behaviour.
     */
    static <T> Function<T, NonBlockingLifecycle> compose(Function<T, CompletionStage<Void>> start, Function<T, CompletionStage<Void>> stop, Function<T, CompletionStage<Void>> close) {
        return new Function<>() {
            @Override
            public NonBlockingLifecycle apply(T value) {
                return new NonBlockingLifecycle() {
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
                    public CompletionStage<Void> start() {
                        // If start fails, reset state
                        return this.state.compareAndSet(State.STOPPED, State.STARTING) ? start.apply(value).whenComplete((ignore, exception) -> this.state.compareAndSet(State.STARTING, (exception == null) ? State.STARTED : State.STOPPED)) : COMPLETED;
                    }

                    @Override
                    public CompletionStage<Void> stop() {
                        // Set state to stopped, even if exceptional
                        return this.state.compareAndSet(State.STARTED, State.STOPPING) ? stop.apply(value).whenComplete((ignore, exception) -> this.state.compareAndSet(State.STOPPING, State.STOPPED)) : COMPLETED;
                    }

                    @Override
                    public CompletionStage<Void> close() {
                        // Terminal state
                        return (this.state.getAndSet(State.CLOSED) != State.CLOSED) ? close.apply(value) : COMPLETED;
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
    static <T> Function<T, NonBlockingLifecycle> combine(List<Function<? super T, ? extends NonBlockingLifecycle>> lifecycleProviders) {
        return new Function<>() {
            @Override
            public NonBlockingLifecycle apply(T value) {
                if (lifecycleProviders.isEmpty()) return NonBlockingLifecycle.NONE;
                List<NonBlockingLifecycle> lifecycles = new ArrayList<>(lifecycleProviders.size());
                for (Function<? super T, ? extends NonBlockingLifecycle> lifecycleProvider : lifecycleProviders) {
                    lifecycles.add(lifecycleProvider.apply(value));
                }
                return new NonBlockingLifecycle() {
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
                    public CompletionStage<Void> start() {
                        CompletionStage<Void> result = COMPLETED;
                        for (NonBlockingLifecycle lifecycle : lifecycles) {
                            if (lifecycle.isStopped()) {
                                result = result.thenApply(new DiscardingFunction<>(lifecycle)).thenCompose(NonBlockingLifecycle::start);
                            }
                        }
                        return result;
                    }

                    @Override
                    public CompletionStage<Void> stop() {
                        CompletionStage<Void> result = COMPLETED;
                        // Stop in reverse order
                        ListIterator<NonBlockingLifecycle> iterator = lifecycles.listIterator(lifecycles.size());
                        while (iterator.hasPrevious()) {
                            NonBlockingLifecycle lifecycle = iterator.previous();
                            if (lifecycle.isStarted()) {
                                result = result.thenApply(new DiscardingFunction<>(lifecycle)).thenCompose(NonBlockingLifecycle::stop);
                            }
                        }
                        return result;
                    }

                    @Override
                    public CompletionStage<Void> close() {
                        CompletionStage<Void> result = COMPLETED;
                        // Close in reverse order
                        ListIterator<NonBlockingLifecycle> iterator = lifecycles.listIterator(lifecycles.size());
                        while (iterator.hasPrevious()) {
                            NonBlockingLifecycle lifecycle = iterator.previous();
                            if (!lifecycle.isClosed()) {
                                result = result.thenApply(new DiscardingFunction<>(lifecycle)).thenCompose(NonBlockingLifecycle::close);
                            }
                        }
                        return result;
                    }
                };
            }
        };
    }
}
