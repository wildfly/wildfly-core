/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting.management;

import org.jboss.as.protocol.mgmt.support.ManagementChannelShutdownHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.service.StartException;

/**
 * The main purpose of this class is to setup proper service dependencies for shutdown.
 *
 * In general all services accepting management requests (http or remoting) register here. On shutdown services
 * registering the shutdown handles need to use the {@code #prepareShutdown} and then {@code awaitShutdown()} to prevent
 * remoting services to shut down before active management requests could complete.
 *
 * @author Emanuel Muckenhuber
 */
public class ManagementRequestTracker {

    private volatile boolean shutdown;
    private final List<ManagementChannelShutdownHandle> trackers = Collections.synchronizedList(new ArrayList<ManagementChannelShutdownHandle>());

    ManagementRequestTracker() {
        //
    }

    synchronized void reset() throws StartException {
        shutdown = false;
    }

    synchronized void stop() {
        shutdown = true;
        final List<ManagementChannelShutdownHandle> trackers = new ArrayList<>(this.trackers);
        for (final ManagementChannelShutdownHandle tracker : trackers) {
            tracker.shutdownNow();
        }
        this.trackers.clear();
        notifyAll();
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public synchronized void prepareShutdown() {
        shutdown = true;
        final List<ManagementChannelShutdownHandle> trackers = new ArrayList<>(this.trackers);
        for (final ManagementChannelShutdownHandle tracker : trackers) {
            tracker.shutdown();
        }
    }

    public synchronized void registerTracker(final ManagementChannelShutdownHandle tracker) {
        if (!shutdown) {
            trackers.add(tracker);
        } else {
            tracker.shutdown();
        }
    }

    public synchronized void unregisterTracker(final ManagementChannelShutdownHandle tracker) {
        trackers.remove(tracker);
        notifyAll();
    }

    public synchronized boolean awaitShutdown(long timeout, TimeUnit timeUnit) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        for (;;) {
            if (shutdown && trackers.isEmpty()) {
                return true;
            }
            final long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            wait(remaining);
        }
    }

}
