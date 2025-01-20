/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.embedded;


import java.util.concurrent.Future;

import org.jboss.as.host.controller.AbstractHostControllerBootstrap;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;

/**
 * Embedded variant of {@link org.jboss.as.host.controller.AbstractHostControllerBootstrap}.
 *
 * @author Ken Wills (c) 2015 Red Hat Inc.
 */
public final class EmbeddedHostControllerBootstrap extends AbstractHostControllerBootstrap {

    private final ShutdownHook shutdownHook;
    public EmbeddedHostControllerBootstrap(final HostControllerEnvironment environment, final String authCode) {
        this(environment, authCode, new ShutdownHook());
    }

    private EmbeddedHostControllerBootstrap(final HostControllerEnvironment environment, final String authCode,
                                            final ShutdownHook shutdownHook) {
        super(environment, authCode, shutdownHook);
        this.shutdownHook = shutdownHook;
    }

    /**
     * Start the host controller services.
     *
     * @param extraServices any extra services to launch as part of bootstrap
     */
    public Future<ServiceContainer> bootstrap(ServiceActivator... extraServices) {
        try {
            return bootstrap(true, extraServices);
        } catch (RuntimeException | Error e) {
            shutdownHook.shutdown();
            throw e;
        }
    }

    /**
     * Notification that overall embedded Host Controller startup of which a call
     * to {@link #bootstrap(ServiceActivator...)} was a part has failed.
     */
    public void failed() {
        shutdownHook.shutdown();
    }

}
