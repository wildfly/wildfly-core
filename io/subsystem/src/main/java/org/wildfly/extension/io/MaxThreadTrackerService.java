/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.HashMap;
import java.util.Map;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Tracks all worker services.
 */
class MaxThreadTrackerService implements Service<Integer> {
    private Map<String, Integer> workers = new HashMap<String, Integer>();
    private volatile int total;

    @Override
    public void start(StartContext startContext) throws StartException {
        // This is hackish but worker state is tracked independently of this service's lifecycle.
        // Interaction is as follows:
        // 1. IO Root add handler installs the service and registers a step handler
        // 2. Child worker A add handler registers its max thread count, incrementing the total
        // 3. Child worker B add handler registers its max thread count, incrementing the total
        // 4. Root's follow-up step handler starts this service
        // 5. Consumers of this service can now inject the total max thread count
    }

    @Override
    public void stop(StopContext stopContext) {
    }

    void registerWorkerMax(String name, int max) {
        synchronized (workers) {
            Integer prev = workers.get(name);
            if (prev != null) {
                max -= prev.intValue();
            }
            workers.put(name, max);
            total += max;
        }
    }

    // Not needed until workers support dynamic removal (currently reload required)
    void unregisterWorkerMax(String name) {
        synchronized (workers) {
            Integer val = workers.remove(name);
            if (val != null) {
                total -= val;
            }
        }
    }

    @Override
    public Integer getValue() throws IllegalStateException, IllegalArgumentException {
        return total;
    }
}
