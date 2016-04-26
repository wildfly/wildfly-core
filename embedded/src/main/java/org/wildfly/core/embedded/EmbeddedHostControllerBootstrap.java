/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.core.embedded;

import java.util.concurrent.CountDownLatch;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateJmx;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostControllerService;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.server.FutureServiceContainer;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
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
    private FutureServiceContainer futureContainer;

    public EmbeddedHostControllerBootstrap(FutureServiceContainer futureContainer, final HostControllerEnvironment environment, final String authCode) {
        this.environment = environment;
        this.authCode = authCode;
        this.shutdownHook = new ShutdownHook();
        this.serviceContainer = shutdownHook.register();
        this.futureContainer = futureContainer;
    }

    public FutureServiceContainer bootstrap() throws Exception {
        try {
            final HostRunningModeControl runningModeControl = environment.getRunningModeControl();
            final ControlledProcessState processState = new ControlledProcessState(true);
            shutdownHook.setControlledProcessState(processState);
            ServiceTarget target = serviceContainer.subTarget();

            final ServiceController<ControlledProcessStateService> serviceController = ControlledProcessStateService.addService(target, processState);
            ControlledProcessStateJmx.registerMBean(processState);
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