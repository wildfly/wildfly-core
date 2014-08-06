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
package org.jboss.as.server.requestcontroller;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.shutdown.ServerActivityListener;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A representation of an entry point into the application server, represented as both a deployment
 * name and an entry point name.
 *
 * This should only be created using the top level name, as it does not generally make sense to shut
 * down individual parts of a deployment.
 *
 * Note that requests are tracked at two levels, both at the entry point level and the request controller level.
 * This allows for individual deployments/interfaces to be gracefully suspended, and also allows for the global
 * request controller to limit the total number of active requests.
 *
 * @author Stuart Douglas
 */
public class ControlPoint {

    private static final AtomicIntegerFieldUpdater<ControlPoint> activeRequestCountUpdater = AtomicIntegerFieldUpdater.newUpdater(ControlPoint.class, "activeRequestCount");
    private static final AtomicReferenceFieldUpdater<ControlPoint, ServerActivityListener> listenerUpdater = AtomicReferenceFieldUpdater.newUpdater(ControlPoint.class, ServerActivityListener.class, "listener");

    private final GlobalRequestController controller;
    private final String deployment;
    private final String entryPoint;

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
    private volatile ServerActivityListener listener = null;

    /**
     * The number of services that are using this entry point.
     * This is a deployment time measurement, not a runtime one
     */
    private int referenceCount = 0;

    ControlPoint(GlobalRequestController controller, String deployment, String entryPoint) {
        this.controller = controller;
        this.deployment = deployment;
        this.entryPoint = entryPoint;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public String getDeployment() {
        return deployment;
    }

    /**
     * Pause the current entry point, and invoke the provided listener when all current requests have finished.
     *
     * @param requestCountListener The listener to invoke
     */
    public void pause(ServerActivityListener requestCountListener) {
        if(paused) {
            throw ServerLogger.ROOT_LOGGER.serverAlreadyPaused();
        }
        this.paused = true;
        listenerUpdater.set(this, requestCountListener);
        if (activeRequestCountUpdater.get(this) == 0) {
            if(listenerUpdater.compareAndSet(this, requestCountListener, null)) {
                requestCountListener.requestsComplete();
            }
        }
    }

    /**
     * Cancel the pause operation
     */
     public void resume() {
        this.paused = false;
         ServerActivityListener listener = listenerUpdater.get(this);
        if(listener != null) {
            if(listenerUpdater.compareAndSet(this, listener, null)) {
                listener.unPaused();
            }
        }
    }


    /**
     * All tasks entering the system via this entry point must call this method. If it returns REJECTED then the
     * task cannot be run, and its failure should be signaled back to the originator.
     *
     * If it returns {@code RUN} then the task should proceed as normal, and the {@link #requestComplete()} method
     * must be called once the task is complete, usually via a try/finally construct.
     *
     */
    public  RunResult beginRequest() throws Exception {
        if(paused) {
            return RunResult.REJECTED;
        }
        activeRequestCountUpdater.incrementAndGet(this);
        RunResult runResult = controller.beginRequest();
        if(runResult == RunResult.REJECTED) {
            decreaseRequestCount();
        }
        return runResult;
    }

    /**
     * Method that should be invoked once (and only once) to signify that a request has finished.
     *
     * This cannot be done automatically when the handleRequest method completes, as some
     *
     */
    public void requestComplete() {
        decreaseRequestCount();
        controller.requestComplete();
    }

    private void decreaseRequestCount() {
        int result = activeRequestCountUpdater.decrementAndGet(this);
        if(paused && result == 0) {
            ServerActivityListener listener = listenerUpdater.get(this);
            if(listener != null) {
                if(listenerUpdater.compareAndSet(this, listener, null)) {
                    listener.requestsComplete();
                }
            }
        }
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
