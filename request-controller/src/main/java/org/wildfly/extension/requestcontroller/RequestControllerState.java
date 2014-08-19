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
