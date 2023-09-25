/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * A service to be able to access the host controller environment
 *
 * @author Kabir Khan
 */
public class HostControllerEnvironmentService implements Service<HostControllerEnvironment> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "environment");

    public static void addService(HostControllerEnvironment hostControllerEnvironment, ServiceTarget target) {
        target.addService(SERVICE_NAME, new HostControllerEnvironmentService(hostControllerEnvironment))
                .install();
    }

    private final HostControllerEnvironment environment;

    public HostControllerEnvironmentService(HostControllerEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void start(StartContext context) throws StartException {
        //no-op
    }

    @Override
    public void stop(StopContext context) {
        //no-op
    }

    @Override
    public HostControllerEnvironment getValue() throws IllegalStateException, IllegalArgumentException {
        return environment;
    }
}
