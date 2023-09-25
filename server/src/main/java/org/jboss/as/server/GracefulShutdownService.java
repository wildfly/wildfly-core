/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;


/**
 * A service that allows the server to wait until graceful shutdown is complete.
 *
 * This is mainly used to perform graceful shutdown in domain mode, to delay the System.exit() call
 * until the server has suspended.
 *
 * @author Stuart Douglas
 */
public class GracefulShutdownService implements Service<GracefulShutdownService> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("server", "graceful-shutdown-service");

    private final InjectedValue<SuspendController> suspendControllerInjectedValue = new InjectedValue<>();

    private boolean suspend = false;
    private boolean shuttingDown = false;
    private final Object lock = new Object();

    private final OperationListener listener = new OperationListener() {
        @Override
        public void suspendStarted() {
            synchronized (lock) {
                suspend = true;
            }
        }

        @Override
        public void complete() {
            synchronized (lock) {
                suspend = false;
                lock.notifyAll();
            }
        }

        @Override
        public void cancelled() {

            synchronized (lock) {
                suspend = false;
                lock.notifyAll();
            }
        }

        @Override
        public void timeout() {
            synchronized (lock) {
                suspend = false;
                lock.notifyAll();
            }
        }
    };

    @Override
    public void start(StartContext context) throws StartException {
        suspendControllerInjectedValue.getValue().addListener(listener);
    }

    @Override
    public void stop(StopContext context) {
        suspendControllerInjectedValue.getValue().removeListener(listener);
    }

    public void startGracefulShutdown() {
        synchronized (lock) {
            shuttingDown = true;
        }
    }

    public void awaitSuspend() {
        synchronized (lock) {
            while (suspend && shuttingDown) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    ServerLogger.AS_ROOT_LOGGER.debug("Exception waiting for graceful shutdown", e);
                }
            }
        }
    }

    public InjectedValue<SuspendController> getSuspendControllerInjectedValue() {
        return suspendControllerInjectedValue;
    }

    @Override
    public GracefulShutdownService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
