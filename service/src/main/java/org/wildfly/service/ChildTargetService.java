/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * Service that facilitates service installation into a child target on {@link Service#start(StartContext)}.
 * @author Paul Ferraro
 */
public class ChildTargetService implements Service {
    private final ServiceInstaller installer;

    public ChildTargetService(ServiceInstaller installer) {
        this.installer = installer;
    }

    @Override
    public void start(StartContext context) {
        this.installer.install(context.getChildTarget());
    }

    @Override
    public void stop(StopContext context) {
        // Services installed into child target are auto-removed after this service stops.
    }
}
