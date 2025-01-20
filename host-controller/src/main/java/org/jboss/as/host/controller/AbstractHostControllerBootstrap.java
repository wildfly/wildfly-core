/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.server.FutureServiceContainer;
import org.jboss.as.server.jmx.RunningStateJmx;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;

/**
 * Base class for bootstrapping embedded and non-embedded Host Controllers.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Ken Wills (c) 2015 Red Hat Inc.
 */
public abstract class AbstractHostControllerBootstrap {

    private final ShutdownHook shutdownHook;
    private final ServiceContainer serviceContainer;
    private final HostControllerEnvironment environment;
    private final String authCode;

    protected AbstractHostControllerBootstrap(final HostControllerEnvironment environment, final String authCode,
                                   final ShutdownHook shutdownHook) {
        this.environment = environment;
        this.authCode = authCode;
        this.shutdownHook = shutdownHook;
        this.serviceContainer = shutdownHook.register();
    }

    /**
     * Bootstrap the host controller.
     *
     * @param embedded {@code true} if the host controller is embedded in another process
     * @param extraServices any extra services to launch as part of bootstrap
     * @return future that will provide the MSC {@link ServiceContainer} for the host controller
     */
    protected final Future<ServiceContainer> bootstrap(boolean embedded, ServiceActivator... extraServices) {
        final HostRunningModeControl runningModeControl = environment.getRunningModeControl();
        final ControlledProcessState processState = new ControlledProcessState(true, embedded);
        shutdownHook.setControlledProcessState(processState);
        ServiceTarget target = serviceContainer.subTarget();

        final ProcessStateNotifier processStateNotifier = ControlledProcessStateService.addService(target, processState);
        RunningStateJmx.registerMBean(processStateNotifier, null, runningModeControl, false);

        final FutureServiceContainer futureServiceContainer = new FutureServiceContainer();
        final HostControllerService hcs = new HostControllerService(environment, runningModeControl, authCode,
                processState, futureServiceContainer, extraServices);
        target.addService(HostControllerService.HC_SERVICE_NAME, hcs).install();
        return futureServiceContainer;
    }

    /**
     * {@link Runtime#addShutdownHook JVM shutdown hook} thread that cleans up the
     * Host Controller services.
     */
    public static final class ShutdownHook extends Thread {

        private final Runnable processExitingCallback;
        private boolean down;
        private ControlledProcessState processState;
        private ServiceContainer container;

        /**
         * Creates a new {@code ShutdownHook} thread.
         */
        public ShutdownHook() {
            this(null);
        }

        /**
         * Creates a new {@code ShutdownHook} thread.
         *
         * @param processExitingCallback runnable to invoke after the {@code ControlledProcessState} has been
         *                               {@link ControlledProcessState#setStopping() set as stopping} but before
         *                               the MSC {@link ServiceContainer} has been {@link ServiceContainer#shutdown() shut down}.
         *                               May be {@code null}.
         */
        public ShutdownHook(Runnable processExitingCallback) {
            this.processExitingCallback = processExitingCallback;
        }

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

        /**
         * Calls {@link #shutdown()}.
         */
        @Override
        public void run() {
            shutdown();
        }

        /**
         * Notifies the {@code ControlledProcessState} for the Host Controller that
         * {@link ControlledProcessState#setStopping() it is stopping}, invokes any {@link Runnable}
         * provided to our {@link ShutdownHook#ShutdownHook(Runnable) constructor}, and
         * {@link ServiceContainer#shutdown() shuts down} the Host Controller's {@link ServiceContainer}.
         */
        public void shutdown() {
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
                    if (processExitingCallback != null) {
                        processExitingCallback.run();
                    }
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
