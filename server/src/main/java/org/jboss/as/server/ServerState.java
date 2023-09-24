/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public enum ServerState {
    /** HostController has told the process controller to start the server and is waiting for the SERVER_AVAILABLE message back from the server */
    BOOTING (true),

    /** The server has sent the available command back to the HostController */
    AVAILABLE (true),

    /** HostController has received the SERVER_AVAILABLE message from the server process, has sent the config
     *  to the server and is waiting for the SERVER_STARTED or SERVER_FAILED message */
    STARTING (false),

    /** The server sent back the SERVER_STARTED message and is up and running */
    STARTED (false),

    /** HostController has told the server to stop and is waiting for the SERVER_STOPPED message */
    STOPPING (false),

    /** We have received the SERVER_STOPPED message */
    STOPPED (false),

    /** We have received the SERVER_START_FAILED message */
    FAILED (true),

    /** We have tried to restart the server several times and received the SERVER_START_FAILED message
     * more times than defined in the max respawn policy */
    MAX_FAILED (false);

    private final boolean restartOnReconnect;

    private ServerState(boolean restartedReconnect) {
        this.restartOnReconnect = restartedReconnect;
    }

    public boolean isRestartOnReconnect() {
        return restartOnReconnect;
    }
}
