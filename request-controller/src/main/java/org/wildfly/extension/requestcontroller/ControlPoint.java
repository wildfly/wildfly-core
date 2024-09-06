/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.requestcontroller;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.jboss.as.server.logging.ServerLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * A representation of an entry point into the application server, represented as both a deployment
 * name and an entry point name.
 * <p/>
 * This should only be created using the top level name, as it does not generally make sense to shut
 * down individual parts of a deployment.
 * <p/>
 * Note that requests are tracked at two levels, both at the entry point level and the request controller level.
 * This allows for individual deployments/interfaces to be gracefully suspended, and also allows for the global
 * request controller to limit the total number of active requests.
 *
 * @author Stuart Douglas
 */
public class ControlPoint {

    private static final AtomicIntegerFieldUpdater<ControlPoint> activeRequestCountUpdater = AtomicIntegerFieldUpdater.newUpdater(ControlPoint.class, "activeRequestCount");
    private static final AtomicReferenceFieldUpdater<ControlPoint, CompletableFuture> pauseUpdater = AtomicReferenceFieldUpdater.newUpdater(ControlPoint.class, CompletableFuture.class, "pauseFuture");

    private final RequestController controller;
    private final String deployment;
    private final String entryPoint;
    private final boolean trackIndividualControlPoints;

    /**
     * The number of active requests that are using this entry point
     */
    @SuppressWarnings("unused")
    private volatile int activeRequestCount = 0;

    /**
     * If this entry point is paused
     */
    private volatile boolean paused = false;

    @SuppressWarnings("unused")
    private volatile CompletableFuture<Void> pauseFuture = null;

    /**
     * The number of services that are using this entry point.
     * This is a deployment time measurement, not a runtime one
     */
    private int referenceCount = 0;

    ControlPoint(RequestController controller, String deployment, String entryPoint, boolean trackIndividualControlPoints) {
        this.controller = controller;
        this.deployment = deployment;
        this.entryPoint = entryPoint;
        this.trackIndividualControlPoints = trackIndividualControlPoints;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public String getDeployment() {
        return deployment;
    }

    /**
     * Pause the current entry point returning a stage that completes when all current requests have completed.
     *
     * If individual control point tracking is not enabled then a completed stage is returned.
     */
    public CompletionStage<Void> pause() {
        if (paused) {
            throw ServerLogger.ROOT_LOGGER.serverAlreadyPaused();
        }
        this.paused = true;
        CompletableFuture<Void> pause = new CompletableFuture<>();
        pauseUpdater.set(this, pause);
        if (activeRequestCountUpdater.get(this) == 0) {
            if (pauseUpdater.compareAndSet(this, pause, null)) {
                pause.complete(null);
            }
        }
        return pause;
    }

    /**
     * Pause the current entry point, and invoke the provided listener when all current requests have finished.
     *
     * If individual control point tracking is not enabled then the listener will be invoked straight away
     *
     * @param requestCountListener The listener to invoke
     * @deprecated Superseded by {@link #pause()}.
     */
    @Deprecated(forRemoval = true)
    public void pause(org.jboss.as.server.suspend.ServerActivityCallback requestCountListener) {
        this.pause().whenComplete((ignore, exception) -> requestCountListener.done());
    }

    /**
     * Cancel the pause operation
     */
    public void resume() {
        this.paused = false;
        CompletableFuture<Void> pause = pauseUpdater.get(this);
        if (pause != null) {
            pauseUpdater.compareAndSet(this, pause, null);
            pause.cancel(false);
        }
    }


    /**
     * All tasks entering the system via this entry point must call this method. If it returns REJECTED then the
     * task cannot be run, and its failure should be signaled back to the originator.
     * <p/>
     * If it returns {@code RUN} then the task should proceed as normal, and the {@link #requestComplete()} method
     * must be called once the task is complete, usually via a try/finally construct.
     */
    public RunResult beginRequest() {
        if (paused) {
            return RunResult.REJECTED;
        }
        if(trackIndividualControlPoints) {
            activeRequestCountUpdater.incrementAndGet(this);
        }
        RunResult runResult = controller.beginRequest(false);
        if (runResult == RunResult.REJECTED) {
            decreaseRequestCount();
        }
        return runResult;
    }



    /**
     * This task should only be called by a thread that has already been accepted from an entry point. It is used when
     * an existing running thread is about to offload to another thread, such as an executor service or async EJB.
     * <p>
     * Note that this can still be rejected if the global request limit has been hit.
     * <p/>
     * If it returns {@code RUN} then the task should proceed as normal, and the {@link #requestComplete()} method
     * must be called once the task is complete, usually via a try/finally construct.
     */
    public RunResult forceBeginRequest() {
        if(trackIndividualControlPoints) {
            activeRequestCountUpdater.incrementAndGet(this);
        }
        return controller.beginRequest(true);
    }

    /**
     * Called when a queued task is executed.
     */
    void beginExistingRequest() {
        if(trackIndividualControlPoints) {
            activeRequestCountUpdater.incrementAndGet(this);
        }
    }

    /**
     * Method that should be invoked once (and only once) to signify that a request has finished.
     * <p/>
     * This cannot be done automatically when the handleRequest method completes, as some
     */
    public void requestComplete() {
        decreaseRequestCount();
        controller.requestComplete();
    }

    private void decreaseRequestCount() {
        if (trackIndividualControlPoints) {
            int result = activeRequestCountUpdater.decrementAndGet(this);
            if (paused && result == 0) {
                CompletableFuture<Void> pause = pauseUpdater.get(this);
                if (pause != null) {
                    if (pauseUpdater.compareAndSet(this, pause, null)) {
                        pause.complete(null);
                    }
                }
            }
        }
    }

    /**
     * Queues a task to run when the request controller allows it. There are two use cases for this:
     * <ol>
     * <li>This allows for requests to be queued instead of dropped when the request limit has been hit</li>
     * <li>Timed jobs that are supposed to execute while the container is suspended can be queued to execute
     * when it resumes</li>
     * </ol>
     * <p>
     * Note that the task will be run within the context of a {@link #beginRequest()} call, if the task
     * is executed there is no need to invoke on the control point again.
     * </p>
     *
     *
     * @param task            The task to run
     * @param timeout         The timeout in milliseconds, if this is larger than zero the task will be timed out after
     *                        this much time has elapsed
     * @param timeoutTask     The task that is run on timeout
     * @param rejectOnSuspend If the task should be rejected if the container is suspended, if this happens the timeout task is invoked immediately
     */
    public void queueTask(Runnable task, Executor taskExecutor, long timeout, Runnable timeoutTask, boolean rejectOnSuspend) {
        controller.queueTask(this, task, taskExecutor, timeout, timeoutTask, rejectOnSuspend, false);
    }

    /**
     * Queues a task to run when the request controller allows it. This allows tasks not to be dropped when the max request
     * limit has been hit. If the container has been suspended then this
     * <p/>
     * Note that the task will be run withing the context of a {@link #beginRequest()} call, if the task
     * is executed there is no need to invoke on the control point again.
     *
     *
     *
     * @param task            The task to run
     * @param taskExecutor    The executor to run the task in
     */
    public void forceQueueTask(Runnable task, Executor taskExecutor) {
        controller.queueTask(this, task, taskExecutor, -1, null, false, true);
    }

    public boolean isPaused() {
        return paused;
    }

    public int getActiveRequestCount() {
        return activeRequestCountUpdater.get(this);
    }

    synchronized int increaseReferenceCount() {
        return ++referenceCount;
    }

    synchronized int decreaseReferenceCount() {
        return --referenceCount;
    }
}
