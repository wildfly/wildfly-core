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

    public enum ServerEnvironmentStatus {
        NORMAL, // environment created or an expected abort
        ERROR // problematic abort
    }

    private final ServerEnvironment serverEnvironment;
    private final ServerEnvironmentStatus serverEnvironmentStatus;

    private ServerEnvironmentWrapper(ServerEnvironment serverEnvironment, ServerEnvironmentStatus serverEnvironmentStatus) {
        this.serverEnvironment = serverEnvironment;
        this.serverEnvironmentStatus = serverEnvironmentStatus;
    }

    ServerEnvironmentWrapper(ServerEnvironment serverEnvironment) {
        this(serverEnvironment, ServerEnvironmentStatus.NORMAL);
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
