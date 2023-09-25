/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessState.State;
import org.jboss.as.controller.ProcessType;

/**
 * Miscellaneous information describing the environment in which an
 * access control decision is being made.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public final class Environment {

    private final Long time = System.currentTimeMillis();
    private final ControlledProcessState processState;
    private final ProcessType processType;

    public Environment(ControlledProcessState processState, ProcessType processType) {
        this.processState = processState;
        this.processType = processType;

    }

    public State getProcessState() {
        return processState.getState();
    }

    public ProcessType getProcessType() {
        return processType;
    }

    public Long getTime() {
        return time;
    }
}
