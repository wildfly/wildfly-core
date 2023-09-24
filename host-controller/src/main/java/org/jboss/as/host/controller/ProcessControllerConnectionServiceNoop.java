/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import org.jboss.as.process.ProcessControllerClient;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Provides a no-op client for (not) interacting with the process controller.
 *
 * @author Ken Wills <kwills@redhat.com>
 */
class ProcessControllerConnectionServiceNoop extends ProcessControllerConnectionService {

    static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "process-controller-connection");

    ProcessControllerConnectionServiceNoop(final HostControllerEnvironment environment, final String authCode) {
        super(environment, authCode);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start(StartContext context) throws StartException {
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stop(StopContext context) {

    }

    /** {@inheritDoc} */
    @Override
    public synchronized ProcessControllerConnectionService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public synchronized ProcessControllerClient getClient() throws IllegalStateException, IllegalArgumentException {
        //XXX this will cause issues outside of admin only.
        return null;
    }
}
