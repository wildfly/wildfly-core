/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

/**
 * @author wangc
 *
 */
public final class ServerEnvironmentWrapper {

    enum ServerEnvironmentStatus {
        NORMAL, // expected abort
        ERROR // problematic abort
    }

    private ServerEnvironment serverEnvironment;
    private ServerEnvironmentStatus serverEnvironmentStatus;

    private ServerEnvironmentWrapper(ServerEnvironment serverEnvironment, ServerEnvironmentStatus serverEnvironmentStatus) {
        this.serverEnvironment = serverEnvironment;
        this.serverEnvironmentStatus = serverEnvironmentStatus;
    }

    ServerEnvironmentWrapper(ServerEnvironment serverEnvironment) {
        this(serverEnvironment, null);
    }

    ServerEnvironmentWrapper(ServerEnvironmentStatus serverEnvironmentStatus) {
        this(null, serverEnvironmentStatus);
    }

    public ServerEnvironment getServerEnvironment() {
        return serverEnvironment;
    }

    public ServerEnvironmentStatus getServerEnvironmentStatus() {
        return serverEnvironmentStatus;
    }
}
