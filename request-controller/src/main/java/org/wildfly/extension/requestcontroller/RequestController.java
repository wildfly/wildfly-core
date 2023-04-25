/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.extension.requestcontroller;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;

import org.jboss.as.server.suspend.CountingRequestCountCallback;
import org.jboss.as.server.suspend.ServerActivity;
import org.jboss.as.server.suspend.ServerActivityCallback;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.requestcontroller.logging.RequestControllerLogger;

/**
 * A controller that manages the active requests that are running in the container.
 * <p/>
 * There are two main use cases for this:
 * <p/>
 * 1) Graceful shutdown - When the number of active request reaches zero then the container can be gracefully shut down
 * 2) Request limiting - This allows the total number of requests that are active to be limited.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class RequestController implements Service<RequestController>, ServerActivity {

    static final ServiceName SERVICE_NAME = RequestControllerRootDefinition.REQUEST_CONTROLLER_CAPABILITY.getCapabilityServiceName();

    private static final AtomicIntegerFieldUpdater<RequestController> activeRequestCountUpdater = AtomicIntegerFieldUpdater.newUpdater(RequestController.class, "activeRequestCount");
    private static final AtomicReferenceFieldUpdater<RequestController, ServerActivityCallback> listenerUpdater = AtomicReferenceFieldUpdater.newUpdater(RequestController.class, ServerActivityCallback.class, "listener");

    private volatile int maxRequestCount = -1;

    private volatile int activeRequestCount = 0;

    private volatile boolean paused = false;

    private final Map<ControlPointIdentifier, ControlPoint> entryPoints = new HashMap<>();

    @SuppressWarnings("unused")
    private volatile ServerActivityCallback listener = null;

    private final boolean trackIndividualControlPoints;
    private final Supplier<SuspendController> suspendController;

    public RequestController(boolean trackIndividualControlPoints, Supplier<SuspendController> suspendControllerSupplier) {
        this.trackIndividualControlPoints = trackIndividualControlPoints;
        this.suspendController = suspendControllerSupplier;
    }

    @Override
    public void preSuspend(ServerActivityCallback listener) {
        listener.done();
    }

    private Timer timer;

    private final Deque<QueuedTask> taskQueue = new LinkedBlockingDeque<>();

    /**
     * Pause the controller. All existing requests will have a chance to finish, and once all requests are
     * finished the provided listener will be invoked.
     * <p/>
     * While the container is paused no new requests will be accepted.
     *
     * @param requestCountListener The listener that will be notified when all requests are done
     */
    public synchronized void suspended(ServerActivityCallback requestCountListener) {
        this.paused = true;
        listenerUpdater.set(this, requestCountListener);

        if (activeRequestCountUpdater.get(this) == 0) {
            if (listenerUpdater.compareAndSet(this, requestCountListener, null)) {
                requestCountListener.done();
            }
        }
    }

    /**
     * Unpause the server, allowing it to resume normal operations
     */
    @Override
    public synchronized void resume() {
        this.paused = false;
        ServerActivityCallback listener = listenerUpdater.get(this);
        if (listener != null) {
            listenerUpdater.compareAndSet(this, listener, null);
        }
        while (!taskQueue.isEmpty() && (activeRequestCount < maxRequestCount || maxRequestCount < 0)) {
            runQueuedTask(false);
        }
    }

    /**
     * Pauses a given deployment
     *
     * @param deployment The deployment to pause
     * @param listener The listener that will be notified when the pause is complete
     */
    public synchronized void pauseDeployment(final String deployment, ServerActivityCallback listener) {
        final List<ControlPoint> eps = new ArrayList<ControlPoint>();
        for (ControlPoint ep : entryPoints.values()) {
            if (ep.getDeployment().equals(deployment)) {
                if(!ep.isPaused()) {
                    eps.add(ep);
                }
            }
        }
        CountingRequestCountCallback realListener = new CountingRequestCountCallback(eps.size(), listener);
        for (ControlPoint ep : eps) {
            ep.pause(realListener);
        }
    }

    /**
     * resumed a given deployment
     *
     * @param deployment The deployment to resume
     */
    public synchronized void resumeDeployment(final String deployment) {
        for (ControlPoint ep : entryPoints.values()) {
            if (ep.getDeployment().equals(deployment)) {
                ep.resume();
            }
        }
    }

    /**
     * Pauses a given entry point. This can be used to stop all requests though a given mechanism, e.g. all web requests
     *
     * @param controlPoint The control point
     * @param listener   The listener
     */
    public synchronized void pauseControlPoint(final String controlPoint, ServerActivityCallback listener) {
        final List<ControlPoint> eps = new ArrayList<ControlPoint>();
        for (ControlPoint ep : entryPoints.values()) {
            if (ep.getEntryPoint().equals(controlPoint)) {
                if(!ep.isPaused()) {
                    eps.add(ep);
                }
            }
        }
        if(eps.isEmpty()) {
            if(listener != null) {
                listener.done();
            }
        }
        CountingRequestCountCallback realListener = new CountingRequestCountCallback(eps.size(), listener);
        for (ControlPoint ep : eps) {
            ep.pause(realListener);
        }
    }

    /**
     * Resumes a given entry point type;
     *
     * @param entryPoint The entry point
     */
    public synchronized void resumeControlPoint(final String entryPoint) {
        for (ControlPoint ep : entryPoints.values()) {
            if (ep.getEntryPoint().equals(entryPoint)) {
                ep.resume();
            }
        }
    }

    public synchronized RequestControllerState getState() {
        final List<RequestControllerState.EntryPointState> eps = new ArrayList<>();
        for (ControlPoint controlPoint : entryPoints.values()) {
            eps.add(new RequestControllerState.EntryPointState(controlPoint.getDeployment(), controlPoint.getEntryPoint(), controlPoint.isPaused(), controlPoint.getActiveRequestCount()));
        }
        return new RequestControllerState(paused, activeRequestCount, maxRequestCount, eps);
    }

    RunResult beginRequest(boolean force) {
        int maxRequests = maxRequestCount;
        int active = activeRequestCountUpdater.get(this);
        boolean success = false;
        while ((maxRequests <= 0 || active < maxRequests) && (!paused || force)) {
            if (activeRequestCountUpdater.compareAndSet(this, active, active + 1)) {
                success = true;
                break;
            }
            active = activeRequestCountUpdater.get(this);
        }
        if (success) {
            //re-check the paused state
            //this is necessary because there is a race between checking paused and updating active requests
            //if this happens we just call requestComplete(), as the listener can only be invoked once it does not
            //matter if it has already been invoked
            if(!force && paused) {
                requestComplete();
                return RunResult.REJECTED;
            }
            return RunResult.RUN;
        } else {
            return RunResult.REJECTED;
        }
    }

    void requestComplete() {
        runQueuedTask(true);
    }

    private void decrementRequestCount() {

        int result = activeRequestCountUpdater.decrementAndGet(this);
        if (paused) {
            if (paused && result == 0) {
                ServerActivityCallback listener = listenerUpdater.get(this);
                if (listener != null) {
                    if (listenerUpdater.compareAndSet(this, listener, null)) {
                        listener.done();
                    }
                }
            }
        }
    }

    /**
     * Gets an entry point for the given deployment. If one does not exist it will be created. If the request controller is disabled
     * this will return null.
     *
     * Entry points are reference counted. If this method is called n times then {@link #removeControlPoint(ControlPoint)}
     * must also be called n times to clean up the entry points.
     *
     * @param deploymentName The top level deployment name
     * @param entryPointName The entry point name
     * @return The entry point, or null if the request controller is disabled
     */
    public synchronized ControlPoint getControlPoint(final String deploymentName, final String entryPointName) {
        ControlPointIdentifier id = new ControlPointIdentifier(deploymentName, entryPointName);
        ControlPoint ep = entryPoints.get(id);
        if (ep == null) {
            ep = new ControlPoint(this, deploymentName, entryPointName, trackIndividualControlPoints);
            entryPoints.put(id, ep);
        }
        ep.increaseReferenceCount();
        return ep;
    }

    /**
     * Removes the specified entry point
     *
     * @param controlPoint The entry point
     */
    public synchronized void removeControlPoint(ControlPoint controlPoint) {
        if (controlPoint.decreaseReferenceCount() == 0) {
            ControlPointIdentifier id = new ControlPointIdentifier(controlPoint.getDeployment(), controlPoint.getEntryPoint());
            entryPoints.remove(id);
        }
    }

    /**
     * @return The maximum number of requests that can be active at a time
     */
    public int getMaxRequestCount() {
        return maxRequestCount;
    }

    /**
     * Sets the maximum number of requests that can be active at a time.
     * <p/>
     * If this is higher that the number of currently running requests the no new requests
     * will be able to run until the number of active requests has dropped below this level.
     *
     * @param maxRequestCount The max request count
     */
    public void setMaxRequestCount(int maxRequestCount) {
        this.maxRequestCount = maxRequestCount;
        while (!taskQueue.isEmpty() && (activeRequestCount < maxRequestCount || maxRequestCount < 0)) {
            if(!runQueuedTask(false)) {
                break;
            }
        }
    }

    /**
     * @return <code>true</code> If the server is currently pause
     */
    public boolean isPaused() {
        return paused;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        suspendController.get().registerActivity(this);
        timer = new Timer();
    }

    @Override
    public void stop(StopContext stopContext) {
        suspendController.get().unRegisterActivity(this);
        timer.cancel();
        timer = null;
        while (!taskQueue.isEmpty()) {
            QueuedTask t = taskQueue.poll();
            if(t != null) {
                t.run();
            }
        }
    }

    @Override
    public RequestController getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public int getActiveRequestCount() {
        return activeRequestCount;
    }

    void queueTask(ControlPoint controlPoint, Runnable task, Executor taskExecutor, long timeout, Runnable timeoutTask, boolean rejectOnSuspend, boolean forceRun) {
        if(paused) {
            if(rejectOnSuspend && !forceRun) {
                taskExecutor.execute(timeoutTask);
                return;
            }
        }
        QueuedTask queuedTask = new QueuedTask(taskExecutor, task, timeoutTask, controlPoint, forceRun);
        taskQueue.add(queuedTask);
        runQueuedTask(false);
        if(queuedTask.isQueued()) {
            if(timeout > 0) {
                timer.schedule(queuedTask, timeout);
            }
        }
    }

    /**
     * Runs a queued task, if the queue is not already empty.
     *
     * Note that this will decrement the request count if there are no queued tasks to be run
     *
     * @param hasPermit If the caller has already called {@link #beginRequest(boolean force)}
     */
    private boolean runQueuedTask(boolean hasPermit) {
        if (!hasPermit && beginRequest(paused) == RunResult.REJECTED) {
            return false;
        }
        QueuedTask task = null;
        if (!paused) {
            task = taskQueue.poll();
        } else {
            //the container is suspended, but we still need to run any force queued tasks
            task = findForcedTask();
        }
        if (task != null) {
            if(!task.runRequest()) {
                decrementRequestCount();
            }
            return true;
        } else {
            decrementRequestCount();
            return false;
        }
    }

    private QueuedTask findForcedTask() {
        QueuedTask forcedTask = null;
        QueuedTask task;
        List<QueuedTask> storage = new ArrayList<>();
        while (forcedTask == null && (task = taskQueue.poll()) != null) {
            if (task.forceRun) {
                forcedTask = task;
            } else {
                storage.add(task);
            }
        }
        // this screws the order somewhat, but the container is suspending anyway, and the order
        // was never guarenteed. if we push them back onto the front we will need to just go through them again
        taskQueue.addAll(storage);
        return forcedTask;
    }

    private static final class ControlPointIdentifier {
        private final String deployment, name;

        private ControlPointIdentifier(String deployment, String name) {
            this.deployment = deployment;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ControlPointIdentifier that = (ControlPointIdentifier) o;

            if (deployment != null ? !deployment.equals(that.deployment) : that.deployment != null) return false;
            if (name != null ? !name.equals(that.name) : that.name != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = deployment != null ? deployment.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return super.toString();
        }
    }


    private static final class QueuedTask extends TimerTask {

        private final Executor executor;
        private final Runnable task;
        private final Runnable cancelTask;
        private final ControlPoint controlPoint;
        private final boolean forceRun;

        //0 == queued
        //1 == run
        //2 == cancelled
        private final AtomicInteger state = new AtomicInteger(0);

        private QueuedTask(Executor executor, Runnable task, Runnable cancelTask, ControlPoint controlPoint, boolean forceRun) {
            this.executor = executor;
            this.task = task;
            this.cancelTask = cancelTask;
            this.controlPoint = controlPoint;
            this.forceRun = forceRun;
        }

        @Override
        public void run() {
            if(state.compareAndSet(0, 2)) {
                if(cancelTask != null) {
                    try {
                        executor.execute(cancelTask);
                    } catch (Exception e) {
                        //should only happen if the server is shutting down
                        RequestControllerLogger.ROOT_LOGGER.failedToCancelTask(cancelTask, e);
                    }
                }
            }
        }

        public boolean runRequest() {
            if (state.compareAndSet(0, 1)) {
                cancel();
                executor.execute(new ControlPointTask(task, controlPoint));
                return true;
            } else {
                return false;
            }
        }

        boolean isQueued() {
            return state.get() == 0;
        }
    }

}
