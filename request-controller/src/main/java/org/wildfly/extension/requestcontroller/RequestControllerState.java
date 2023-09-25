/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.requestcontroller;

import java.util.Collections;
import java.util.List;

/**
 *
 * Class that can be used to report of the current system state.
 *
 * @author Stuart Douglas
 */
public class RequestControllerState {

    private final boolean paused;
    private final int outstandingRequests;
    private final int maxRequests;
    private final List<EntryPointState> entryPoints;

    public RequestControllerState(boolean paused, int outstandingRequests, int maxRequests, List<EntryPointState> entryPoints) {
        this.paused = paused;
        this.outstandingRequests = outstandingRequests;
        this.maxRequests = maxRequests;
        this.entryPoints = entryPoints;
    }

    public boolean isPaused() {
        return paused;
    }

    public int getOutstandingRequests() {
        return outstandingRequests;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public List<EntryPointState> getEntryPoints() {
        return Collections.unmodifiableList(entryPoints);
    }

    public static class EntryPointState {
        private final String deployment;
        private final String endpoint;
        private final boolean paused;
        private final int outstandingRequests;

        public EntryPointState(String deployment, String endpoint, boolean paused, int outstandingRequests) {
            this.deployment = deployment;
            this.endpoint = endpoint;
            this.paused = paused;
            this.outstandingRequests = outstandingRequests;
        }

        public String getDeployment() {
            return deployment;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public boolean isPaused() {
            return paused;
        }

        public int isOutstandingRequests() {
            return outstandingRequests;
        }
    }
}
