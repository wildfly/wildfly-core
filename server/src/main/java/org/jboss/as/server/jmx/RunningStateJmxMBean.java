/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.jmx;

import org.jboss.as.controller.ControlledProcessState;
import org.wildfly.extension.core.management.client.Process.RunningMode;
import org.wildfly.extension.core.management.client.Process.RunningState;

/**
 * MBean allowing people to register listeners for the controlled processs state.
 * Doing this via that JMX Facade to the management model isn't viable since
 * the facade kicks in too late in the startup, which means that the 'jboss.as' and
 * 'jboss.as.expr' domains are not available yet.
 * <p/>
 * This bean gets registered on first load of server, and can be listened to across server restarts
 * if using an appropriate mechanism (e.g. -javaagent). Note that the typical ways of registering a
 * listener, either using a remote connector or an internal
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public interface RunningStateJmxMBean {
    String OBJECT_NAME = "jboss.root:type=state";

    RunningMode getRunningMode();

    String getProcessState();

    RunningState getRunningState();

    void setProcessState(ControlledProcessState.State oldState, ControlledProcessState.State newState);

    void setRunningState(RunningState oldState, RunningState newState);
}