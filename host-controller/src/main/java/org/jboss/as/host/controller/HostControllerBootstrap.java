/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import java.util.concurrent.CountDownLatch;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.server.jmx.RunningStateJmx;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;

/**
 * Bootstrap of the HostController process.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HostControllerBootstrap {

    private final ShutdownHook shutdownHook;
    private final ServiceContainer serviceContainer;
    private final HostControllerEnvironment environment;
    private final String authCode;

    public HostControllerBootstrap(final HostControllerEnvironment environment, final String authCode) {
        this.environment = environment;
        this.authCode = authCode;
        this.shutdownHook = new ShutdownHook();
        this.serviceContainer = shutdownHook.register();
    }

    /**
     * Start the host controller services.
     *
     * @throws Exception
     */
    public void bootstrap() throws Exception {
        final HostRunningModeControl runningModeControl = environment.getRunningModeControl();
        final ControlledProcessState processState = new ControlledProcessState(true);
        shutdownHook.setControlledProcessState(processState);
        ServiceTarget target = serviceContainer.subTarget();
        ProcessStateNotifier processStateNotifier = ControlledProcessStateService.addService(target, processState);
        RunningStateJmx.registerMBean(processStateNotifier, null, runningModeControl, false);
        final HostControllerService hcs = new HostControllerService(environment, runningModeControl, authCode, processState);
        target.addService(HostControllerService.HC_SERVICE_NAME, hcs).install();
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
                    SystemExiter.logBeforeExit(HostControllerLogger.ROOT_LOGGER::shutdownHookInvoked);
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
