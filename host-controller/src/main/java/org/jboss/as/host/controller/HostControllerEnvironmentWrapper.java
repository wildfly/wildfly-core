/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
