/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management.client;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_OK;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_RESTART_REQUIRED;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPED;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;


import static org.jboss.as.controller.client.helpers.ClientConstants.RUNNING_STATE_ADMIN_ONLY;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNNING_STATE_NORMAL;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNNING_STATE_PRE_SUSPEND;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNNING_STATE_SUSPENDED;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNNING_STATE_SUSPENDING;

/**
 * The overall information of a process (its state, running mode and type).
 * These enums duplicates the ones from the org.jboss.as.controller module to avoid leaking them
 * to the client API.
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public class Process {

    /**
     * The state of the process
     */
    public enum RuntimeConfigurationState {
        STARTING(CONTROLLER_PROCESS_STATE_STARTING),
        RUNNING(CONTROLLER_PROCESS_STATE_OK),
        RELOAD_REQUIRED(CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED),
        RESTART_REQUIRED(CONTROLLER_PROCESS_STATE_RESTART_REQUIRED),
        STOPPING(CONTROLLER_PROCESS_STATE_STOPPING),
        STOPPED(CONTROLLER_PROCESS_STATE_STOPPED);


        private final String state;

        RuntimeConfigurationState(String state) {
            this.state = state;
        }


        @Override
        public String toString() {
            return state;
        }
    }

    public enum RunningState {
        STOPPED(CONTROLLER_PROCESS_STATE_STOPPED),
        STARTING(CONTROLLER_PROCESS_STATE_STARTING),
        PRE_SUSPEND(RUNNING_STATE_PRE_SUSPEND),
        NORMAL(RUNNING_STATE_NORMAL),
        ADMIN_ONLY(RUNNING_STATE_ADMIN_ONLY),
        SUSPENDING(RUNNING_STATE_SUSPENDING),
        SUSPENDED(RUNNING_STATE_SUSPENDED),
        STOPPING(CONTROLLER_PROCESS_STATE_STOPPING);

        private final String state;

        RunningState(String state) {
            this.state = state;
        }

        @Override
        public String toString() {
            return state;
        }
    }

    /**
     * The running mode of the process
     */
    public enum RunningMode {
        ADMIN_ONLY("admin-only"),
        NORMAL("normal");

        private final String mode;

        RunningMode(String mode) {
            this.mode = mode;
        }

        @Override
        public String toString() {
            return mode;
        }

        public static RunningMode from(String runningMode) {
            switch (runningMode) {
                case "ADMIN_ONLY":
                    return ADMIN_ONLY;
                case "NORMAL":
                default:
                    return NORMAL;
            }
        }
    }

    /**
     * The type of the process
     */
    public enum Type {
        DOMAIN_SERVER("DOMAIN_SERVER"),
        EMBEDDED_SERVER("EMBEDDED_SERVER"),
        STANDALONE_SERVER("STANDALONE_SERVER"),
        HOST_CONTROLLER("HOST_CONTROLLER"),
        EMBEDDED_HOST_CONTROLLER("EMBEDDED_HOST_CONTROLLER"),
        APPLICATION_CLIENT("APPLICATION_CLIENT"),
        SELF_CONTAINED("SELF_CONTAINED");

        private final String type;

        Type(String type) {
            this.type = type;
        }

        public static Type from(String processTypeName) {
            switch (processTypeName) {
                case "DOMAIN_SERVER":
                    return DOMAIN_SERVER;
                case "EMBEDDED_SERVER":
                    return EMBEDDED_SERVER;
                case "STANDALONE_SERVER":
                    return STANDALONE_SERVER;
                case "HOST_CONTROLLER":
                    return HOST_CONTROLLER;
                case "EMBEDDED_HOST_CONTROLLER":
                    return EMBEDDED_HOST_CONTROLLER;
                case "APPLICATION_CLIENT":
                    return APPLICATION_CLIENT;
                case "SELF_CONTAINED":
                    return SELF_CONTAINED;
                default:
                    return EMBEDDED_SERVER;
            }
        }
        @Override
        public String toString() {
            return type;
        }
    }
}
