/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.as.server.logging.ServerLogger;
import org.wildfly.common.Assert;
import org.wildfly.common.function.Functions;

/**
 * Orchestrates suspending and resuming of registered server activity.
 * Registered server activity is organized into groups sharing the same suspend priority.
 * Server suspension happens in two phases: prepare + suspend.
 * <ol>
 * <li>Set state to {@code State#PRE_SUSPEND}</li>
 * <li>Iterate over activity groups in priority order (from first to last). For each group:
 *  <ol>
 *  <li>Create prepare stages for each registered server activity</li>
 *  <li>Once all prepare stages within priority group have complete, move on to next priority group</li>
 *  </ol>
 * </li>
 * <li>Set state to {@code State#SUSPENDING}</li>
 * <li>Iterate over activity groups in priority order (from first to last). For each group:
 *  <ol>
 *  <li>Create suspend stages for each registered server activity</li>
 *  <li>Once all suspend stages within priority group have complete, move on to next priority group</li>
 *  </ol>
 * </li>
 * <li>Set state to {@code State#SUSPENDED}</li>
 * </ol>
 * Resuming the suspended server happens in one phase:
 * <ol>
 * <li>Iterate over activity groups in reverse priority order (from last to first). For each group:
 *  <ol>
 *  <li>Create resume stages for each registered server activity</li>
 *  <li>Once all resume stages within priority group have complete, move on to next priority group</li>
 *  </ol>
 * </li>
 * <li>Set state to {@code State#RUNNING}</li>
 * </ol>
 * @author Stuart Douglas
 * @author Paul Ferraro
 */
public class SuspendController implements ServerSuspendController, SuspendableActivityRegistry {
    private static final Supplier<List<SuspendableActivity>> FACTORY = CopyOnWriteArrayList::new;

    // Server activity groups in suspend priority order (max -> min)
    // Initialized as an unmodifiable list of empty activity lists
    private final List<List<SuspendableActivity>> activityGroups = Stream.generate(FACTORY).limit(SuspendPriority.LAST.ordinal() + 1).collect(Collectors.toUnmodifiableList());
    // Index of activity priorities
    private final Map<SuspendableActivity, SuspendPriority> priorities = Collections.synchronizedMap(new IdentityHashMap<>());

    private final List<OperationListener> listeners = new CopyOnWriteArrayList<>();

    private volatile State state = State.SUSPENDED;

    @Override
    public void reset() {
        this.state = State.SUSPENDED;
    }

    @Override
    public CompletionStage<Void> suspend(ServerSuspendContext context) {
        if (this.state == State.SUSPENDED) {
            return SuspendableActivity.COMPLETED;
        }
        this.state = State.PRE_SUSPEND;
        for (OperationListener listener: this.listeners) {
            listener.suspendStarted();
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        // Prepare activity groups in priority order, i.e. first -> last
        phaseStage(this.activityGroups, SuspendableActivity::prepare, context, Functions.discardingBiConsumer()).whenComplete((ignored, prepareException) -> {
            if (prepareException != null) {
                result.completeExceptionally(prepareException);
            } else {
                this.state = State.SUSPENDING;
                // Suspend activity groups in priority order, i.e. first -> last order
                phaseStage(this.activityGroups, SuspendableActivity::suspend, context, Functions.discardingBiConsumer()).whenComplete((ignore, suspendException) -> {
                    if (suspendException != null) {
                        result.completeExceptionally(suspendException);
                    } else {
                        this.state = State.SUSPENDED;
                        result.complete(null);
                        for (OperationListener listener: this.listeners) {
                            listener.complete();
                        }
                    }
                });
            }
        });
        return result;
    }

    @Override
    public CompletionStage<Void> resume(ServerResumeContext context) {
        if (this.state == State.RUNNING) {
            return SuspendableActivity.COMPLETED;
        }
        // Resume activity groups in reverse priority order, i.e. last -> first
        CompletionStage<Void> resumeStage = phaseStage(this::resumeIterator, SuspendableActivity::resume, context, ServerLogger.ROOT_LOGGER::failedToResume);
        resumeStage.whenComplete((ignore, exception) -> {
            if (exception == null) {
                this.state = State.RUNNING;
                for (OperationListener listener: this.listeners) {
                    listener.cancelled();
                }
            }
        });
        return resumeStage;
    }

    private Iterator<List<SuspendableActivity>> resumeIterator() {
        return reverseIterator(this.activityGroups);
    }

    /**
     * Returns the stage for a suspend/resume phase.
     * @param <C> the stage context type
     * @param activityGroups the activity groups in a given iteration order
     * @param phase a function for this phase.
     * @param context the phase context
     * @param exceptionHandler handles exceptions thrown by the phase function
     * @return a completion stage for this phase of the suspend/resume process
     */
    private static <C> CompletionStage<Void> phaseStage(Iterable<List<SuspendableActivity>> activityGroups, BiFunction<SuspendableActivity, C, CompletionStage<Void>> phase, C context, BiConsumer<SuspendableActivity, Throwable> exceptionHandler) {
        // Final stage will complete after all activity for all groups has completed
        CompletableFuture<Void> result = new CompletableFuture<>();
        // Iterate over activity groups (in the order dictated by the caller)
        Iterator<List<SuspendableActivity>> groups = activityGroups.iterator();
        BiConsumer<Void, Throwable> groupCompleter = new BiConsumer<>() {
            @Override
            public void accept(Void ignore, Throwable exception) {
                if (exception != null) {
                    result.completeExceptionally(exception);
                } else if (!groups.hasNext()) {
                    // No more groups
                    result.complete(null);
                } else {
                    // Create stage for next group
                    List<SuspendableActivity> activities = List.copyOf(groups.next());
                    CompletableFuture<Void> groupStage = new CompletableFuture<>();
                    groupStage.whenComplete(this);
                    if (activities.isEmpty()) {
                        // No activities, complete immediately
                        groupStage.complete(null);
                    } else {
                        // Counter used to determine when to complete group stage
                        AtomicInteger groupCounter = new AtomicInteger(activities.size());
                        for (SuspendableActivity activity : activities) {
                            BiConsumer<Void, Throwable> activityCompleter = new BiConsumer<>() {
                                @Override
                                public void accept(Void ignore, Throwable exception) {
                                    if (exception != null) {
                                        try {
                                            exceptionHandler.accept(activity, exception);
                                        } finally {
                                            groupStage.completeExceptionally(exception);
                                        }
                                    } else if (groupCounter.decrementAndGet() == 0) {
                                        // All activities of group have completed
                                        groupStage.complete(null);
                                    }
                                }
                            };
                            try {
                                phase.apply(activity, context).whenComplete(activityCompleter);
                            } catch (Throwable e) {
                                activityCompleter.accept(null, e);
                            }
                        }
                    }
                }
            }
        };
        groupCompleter.accept(null, null);
        return result;
    }

    /**
     * @deprecated Superseded by {@link #resume(ServerResumeContext)}.
     */
    @Deprecated(forRemoval = true)
    public void nonGracefulStart() {
        this.resume(Context.STARTUP).toCompletableFuture().join();
    }

    /**
     * @deprecated Superseded by {@link #resume(ServerResumeContext)}.
     */
    @Deprecated(forRemoval = true)
    public void resume() {
        this.resume(Context.RUNNING).toCompletableFuture().join();
    }

    /**
     * @deprecated Superseded by {@link #suspend(ServerSuspendContext)} using {@link CompletableFuture#completeOnTimeout(Object, long, TimeUnit)}.
     */
    @Deprecated(forRemoval = true)
    public void suspend(long timeoutMillis) {
        ServerLogger.ROOT_LOGGER.suspendingServer(timeoutMillis, TimeUnit.MILLISECONDS);
        CompletableFuture<Void> suspend = this.suspend(Context.RUNNING).toCompletableFuture();
        if (timeoutMillis >= 0) {
            suspend.completeOnTimeout(null, timeoutMillis, TimeUnit.MILLISECONDS);
        }
        suspend.join();
    }

    private static <E> Iterator<E> reverseIterator(List<E> list) {
        ListIterator<E> iterator = list.listIterator(list.size());
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasPrevious();
            }

            @Override
            public E next() {
                return iterator.previous();
            }

            @Override
            public void remove() {
                iterator.remove();
            }

            @Override
            public void forEachRemaining(Consumer<? super E> action) {
                while (this.hasNext()) {
                    action.accept(this.next());
                }
            }
        };
    }

    @Override
    public void registerActivity(SuspendableActivity activity, SuspendPriority priority) {
        Assert.checkNotNullParam("activity", activity);
        Assert.checkNotNullParam("priority", priority);
        if ((priority.ordinal() < SuspendPriority.FIRST.ordinal()) || (priority.ordinal() > SuspendPriority.LAST.ordinal())) {
            throw new IllegalArgumentException(String.valueOf(priority.ordinal()));
        }
        if (this.priorities.putIfAbsent(activity, priority) == null) {
            this.activityGroups.get(priority.ordinal()).add(activity);
            if (this.state != State.RUNNING) {
                // if the activity is added when we are not running we just immediately suspend it
                // this should only happen at boot, so there should be no outstanding requests anyway
                // note that this means there is no execution group grouping of these calls.
                activity.suspend(Context.STARTUP).toCompletableFuture().join();
            }
        }
    }

    @Override
    public void unregisterActivity(SuspendableActivity activity) {
        SuspendPriority priority = this.priorities.remove(activity);
        if (priority != null) {
            this.activityGroups.get(priority.ordinal()).remove(activity);
        }
    }

    @Override
    public State getState() {
        return this.state;
    }

    @Override
    public void addListener(OperationListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(OperationListener listener) {
        this.listeners.remove(listener);
    }

    @Deprecated(forRemoval = true)
    public void setStartSuspended(boolean startSuspended) {
        this.reset();
    }

    /**
     * Registers the given {@link ServerActivity} with this controller
     * @param activity the activity. Cannot be {@code null}
     * @throws IllegalArgumentException if {@code activity} is {@code null} of if its
     *                                  {@link ServerActivity#getExecutionGroup() getExecutionGroup()} method
     *                                  returns a value outside of that method's documented legal range.
     * @deprecated Superseded by {@link #registerActivity(SuspendableActivity)}.
     */
    @Deprecated(forRemoval = true)
    public void registerActivity(final ServerActivity activity) {
        this.registerActivity(activity, SuspendPriority.of(activity.getExecutionGroup()));
    }

    /**
     * @deprecated Superseded by {@link #unregisterActivity(SuspendableActivity)}.
     */
    @Deprecated(forRemoval = true)
    public void unRegisterActivity(final ServerActivity activity) {
        this.unregisterActivity(activity);
    }
}
