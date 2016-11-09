/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.host.controller;

/**
 * @author wangc
 *
 */
public final class HostControllerEnvironmentWrapper {

    enum HostControllerEnvironmentStatus {
        NORMAL, // expected abort
        ERROR // problematic abort
    }

    private HostControllerEnvironment hostControllerEnvironment;
    private HostControllerEnvironmentStatus hostControllerEnvironmentStatus;

    private HostControllerEnvironmentWrapper(HostControllerEnvironment hostControllerEnvironment, HostControllerEnvironmentStatus hostControllerEnvironmentStatus) {
        this.hostControllerEnvironment = hostControllerEnvironment;
        this.hostControllerEnvironmentStatus = hostControllerEnvironmentStatus;
    }

    HostControllerEnvironmentWrapper(HostControllerEnvironment hostControllerEnvironment) {
        this(hostControllerEnvironment, null);
    }

    HostControllerEnvironmentWrapper(HostControllerEnvironmentStatus hostControllerEnvironmentStatus) {
        this(null, hostControllerEnvironmentStatus);
    }

    public HostControllerEnvironment getHostControllerEnvironment() {
        return hostControllerEnvironment;
    }

    public HostControllerEnvironmentStatus getHostControllerEnvironmentStatus() {
        return hostControllerEnvironmentStatus;
    }
}
