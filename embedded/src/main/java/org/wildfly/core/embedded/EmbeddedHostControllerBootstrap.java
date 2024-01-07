/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

import java.beans.PropertyChangeListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostControllerService;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.server.FutureServiceContainer;
import org.jboss.as.server.jmx.RunningStateJmx;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;

/**
 * Bootstrap of the Embedded HostController process.
 *
 * @author Ken Wills (c) 2015 Red Hat Inc.
 */
public class EmbeddedHostControllerBootstrap {

    private final ShutdownHook shutdownHook;
    private final ServiceContainer serviceContainer;
    private final HostControllerEnvironment environment;
    private final String authCode;
    private final FutureServiceContainer futureContainer;

    public EmbeddedHostControllerBootstrap(FutureServiceContainer futureContainer, final HostControllerEnvironment environment, final String authCode) {
        this.environment = environment;
        this.authCode = authCode;
        this.shutdownHook = new ShutdownHook();
        this.serviceContainer = shutdownHook.register();
        this.futureContainer = futureContainer;
    }

    public FutureServiceContainer bootstrap(PropertyChangeListener processStateListener, AtomicReference<ProcessStateNotifier> notifierRef) throws Exception {
        try {
            final HostRunningModeControl runningModeControl = environment.getRunningModeControl();
            final ControlledProcessState processState = new ControlledProcessState(true);
            shutdownHook.setControlledProcessState(processState);
            ServiceTarget target = serviceContainer.subTarget();

            final ProcessStateNotifier processStateNotifier = ControlledProcessStateService.addService(target, processState);
            processStateNotifier.addPropertyChangeListener(processStateListener);
            notifierRef.set(processStateNotifier);
            RunningStateJmx.registerMBean(processStateNotifier, null, runningModeControl, false);
            final HostControllerService hcs = new HostControllerService(environment, runningModeControl, authCode, processState, futureContainer);
            target.addService(HostControllerService.HC_SERVICE_NAME, hcs).install();
            return futureContainer;
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
