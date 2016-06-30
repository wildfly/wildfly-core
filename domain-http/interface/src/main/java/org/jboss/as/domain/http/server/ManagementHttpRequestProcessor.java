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

package org.jboss.as.domain.http.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jboss.as.domain.http.server.logging.HttpServerLogger;
import org.jboss.as.protocol.mgmt.support.ManagementChannelShutdownHandle;

/**
 * This class tracks active http management requests and provides listeners to coordinate the shutdown of the server. Its
 * main purpose is in conjunction with the {@code HttpShutdownService} and the {@code ManagementRequestTracker},
 * which prevent the http server from closing the connections before all active operations completed.
 *
 * @author Emanuel Muckenhuber
 */
public class ManagementHttpRequestProcessor implements ManagementChannelShutdownHandle {

    private volatile int state;
    private final List<ShutdownListener> listeners = new ArrayList<>();

    private static final int CLOSED = 1 << 31;
    private volatile CountDownLatch latch = new CountDownLatch(1);
    private static final AtomicIntegerFieldUpdater<ManagementHttpRequestProcessor> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ManagementHttpRequestProcessor.class, "state");

    @Override
    public void shutdown() {
        prepareShutdown();
    }

    @Override
    public void shutdownNow() {
        prepareShutdown();
    }

    @Override
    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        boolean completed = latch.await(timeout, unit);
        if (!completed) {
            HttpServerLogger.ROOT_LOGGER.debugf("ShutdownListener(s) %s have not completed within %d %s", listeners, timeout, unit);
        }
        return completed;
    }

    /**
     * Prepare the shutdown, disallowing new requests.
     */
    // This gets called in HttpShutdownService#stop() to signal that the server is about to stop
    void prepareShutdown() {
        int oldState, newState;
        do {
            oldState = state;
            if ((oldState & CLOSED) != 0) {
                return;
            }
            newState = oldState | CLOSED;
        } while (!stateUpdater.compareAndSet(this, oldState, newState));
        // If there no active requests notify listeners directly
        if (newState == CLOSED) {
            handleCompleted();
        }
    }

    /**
     * Add a shutdown listener, which gets called when all requests completed on shutdown.
     *
     * @param listener    the shutdown listener
     */
    public synchronized void addShutdownListener(ShutdownListener listener) {
        if (state == CLOSED) {
            listener.handleCompleted();
        } else {
            listeners.add(listener);
        }
    }

    /**
     * Notify all shutdown listeners that the shutdown completed.
     */
    protected synchronized void handleCompleted() {
        latch.countDown();
        for (final ShutdownListener listener : listeners) {
            listener.handleCompleted();
        }
        listeners.clear();
    }

    /**
     * This gets called when we receive a new http mgmt request and returns whether
     * the request can process or should return a {@code 503} response when the server
     * is about to shutdown.
     *
     * @return {@code true} if the request can be processed; {@code false} if the server is about to shut down
     */
    protected boolean beginRequest() {
        int oldState, newState;
        for (;;) {
            oldState = state;
            if ((oldState & CLOSED) != 0) {
                return false;
            }
            newState = oldState + 1;
            if (newState == Integer.MAX_VALUE) {
                return false;
            }
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return true;
            }
        }
    }

    /**
     * End a request and call the {@link #handleCompleted()} if the server is
     * shutdown and there are no more active requests.
     */
    protected void endRequest() {
        int oldState, newState;
        do {
            oldState = state;
            newState = oldState - 1;
        } while (!stateUpdater.compareAndSet(this, oldState, newState));
        if (newState == CLOSED) {
            handleCompleted();
        }
    }

    public interface ShutdownListener {

        void handleCompleted();

    }

}
