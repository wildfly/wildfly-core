/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.embedded;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostControllerService;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.server.FutureServiceContainer;
import org.jboss.as.server.jmx.RunningStateJmx;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;

/**
 * Embedded variant of {@link org.jboss.as.host.controller.HostControllerBootstrap}.  TODO WFCORE-7143 see if these can be better unified.
 *
 * @author Ken Wills (c) 2015 Red Hat Inc.
 */
public class EmbeddedHostControllerBootstrap {

    private final ShutdownHook shutdownHook;
    private final ServiceContainer serviceContainer;
    private final HostControllerEnvironment environment;
    private final String authCode;

    public EmbeddedHostControllerBootstrap(final HostControllerEnvironment environment, final String authCode) {
        this.environment = environment;
        this.authCode = authCode;
        this.shutdownHook = new ShutdownHook();
        this.serviceContainer = shutdownHook.register();
    }

    public Future<ServiceContainer> bootstrap(ServiceActivator... extraServices) throws Exception {
        try {
            final HostRunningModeControl runningModeControl = environment.getRunningModeControl();
            final ControlledProcessState processState = new ControlledProcessState(true, true);
            shutdownHook.setControlledProcessState(processState);
            ServiceTarget target = serviceContainer.subTarget();

            final org.jboss.as.controller.ProcessStateNotifier processStateNotifier = ControlledProcessStateService.addService(target, processState);
            RunningStateJmx.registerMBean(processStateNotifier, null, runningModeControl, false);

            final FutureServiceContainer futureServiceContainer = new FutureServiceContainer();
            final HostControllerService hcs = new HostControllerService(environment, runningModeControl, authCode,
                    processState, futureServiceContainer, extraServices);
            target.addService(HostControllerService.HC_SERVICE_NAME, hcs).install();
            return futureServiceContainer;
        } catch (RuntimeException | Error e) {
            shutdownHook.run();
            throw e;
        }
    }

    public void failed() {
        shutdownHook.run();
    }

    private static class ShutdownHook extends Thread {
        private boolean down;
        private ControlledProcessState processState;
        private ServiceContainer container;

        private ServiceContainer register() {

            Runtime.getRuntime().addShutdownHook(this);
            synchronized (this) {
                if (!down) {
                    container = ServiceContainer.Factory.create("host-controller", false);
                    return container;
                } else {
                    throw new IllegalStateException();
                }
            }
        }

        private synchronized void setControlledProcessState(final ControlledProcessState ps) {
            this.processState = ps;
        }

        @Override
        public void run() {
            final ServiceContainer sc;
            final ControlledProcessState ps;
            synchronized (this) {
                down = true;
                sc = container;
                ps = processState;
            }
            try {
                if (ps != null) {
                    ps.setStopping();
                }
            } finally {
                if (sc != null) {
                    final CountDownLatch latch = new CountDownLatch(1);
                    sc.addTerminateListener(new ServiceContainer.TerminateListener() {
                        @Override
                        public void handleTermination(Info info) {
                            latch.countDown();
                        }
                    });
                    sc.shutdown();
                    // wait for all services to finish.
                    for (;;) {
                        try {
                            latch.await();
                            break;
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        }
    }

}
