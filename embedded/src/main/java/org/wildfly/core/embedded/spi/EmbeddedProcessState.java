/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded.spi;

/**
 * Analogue to {@code org.jboss.as.controller.ControlledProcessState.State} for use
 * in an embedded process.
 */
public enum EmbeddedProcessState {
    /**
     * The process is starting and its runtime state is being made consistent with its persistent configuration.
     */
    STARTING("starting"),
    /**
     * The process is started, is running normally and has a runtime state consistent with its persistent configuration.
     */
    RUNNING("running"),
    /**
     * The process requires a stop and re-start of its root service (but not a full process restart) in order to
     * ensure stable operation and/or to bring its running state in line with its persistent configuration. A
     * stop and restart of the root service (also known as a 'reload') will result in the removal of all other
     * services and creation of new services based on the current configuration, so its affect on availability to
     * handle external requests is similar to that of a full process restart. However, a reload can execute more
     * quickly than a full process restart.
     */
    RELOAD_REQUIRED("reload-required"),
    /**
     * The process must be terminated and replaced with a new process in order to ensure stable operation and/or to bring
     * the running state in line with the persistent configuration.
     */
    RESTART_REQUIRED("restart-required"),
    /** The process is stopping. */
    STOPPING("stopping"),
    /** The process is stopped */
    STOPPED("stopped");

    private final String stringForm;

    EmbeddedProcessState(final String stringForm) {
        this.stringForm = stringForm;
    }

    @Override
    public String toString() {
        return stringForm;
    }
}
