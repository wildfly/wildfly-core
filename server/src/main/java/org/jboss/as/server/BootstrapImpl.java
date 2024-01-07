/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.server.jmx.RunningStateJmx;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.threads.AsyncFuture;
import org.jboss.threads.AsyncFutureTask;
import org.jboss.threads.JBossExecutors;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The bootstrap implementation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class BootstrapImpl implements Bootstrap {

    private static final String SIGTERM_SUSPEND_TIMEOUT_PROP = "org.wildfly.sigterm.suspend.timeout";

    private static final int MAX_THREADS = ServerEnvironment.getBootstrapMaxThreads();
    private final ShutdownHook shutdownHook;
    private final ServiceContainer container;

    public BootstrapImpl() {
        this.shutdownHook = new ShutdownHook();
        this.container = shutdownHook.register();
    }

    @Override
    public AsyncFuture<ServiceContainer> bootstrap(final Configuration configuration, final List<ServiceActivator> extraServices) {
        assert !shutdownHook.down;
        try {
            return internalBootstrap(configuration, extraServices);
        } catch (RuntimeException | Error e) {
            // Clean up our container
            shutdownHook.shutdown(true);
            throw e;
        }
    }

    private AsyncFuture<ServiceContainer> internalBootstrap(final Configuration configuration, final List<ServiceActivator> extraServices) {
        try {
            final Object value = ManagementFactory.getPlatformMBeanServer().getAttribute(new ObjectName("java.lang", "type", "OperatingSystem"), "MaxFileDescriptorCount");
            final long fdCount = Long.parseLong(value.toString());
            if (fdCount < 4096L) {
                ServerLogger.FD_LIMIT_LOGGER.fdTooLow(fdCount);
            }
        } catch (Throwable ignored) {}

        assert configuration != null : "configuration is null";

        // AS7-6381 set this property so we can get it out of the launch scripts
        String resolverWarning = WildFlySecurityManager.getPropertyPrivileged("org.jboss.resolver.warning", null);
        if (resolverWarning == null) {
            WildFlySecurityManager.setPropertyPrivileged("org.jboss.resolver.warning", "true");
        }

        final ModuleLoader moduleLoader = configuration.getModuleLoader();
        final Bootstrap.ConfigurationPersisterFactory configurationPersisterFactory = configuration.getConfigurationPersisterFactory();
        assert configurationPersisterFactory != null : "configurationPersisterFactory is null";

        try {
            Module.registerURLStreamHandlerFactoryModule(moduleLoader.loadModule(ModuleIdentifier.create("org.jboss.vfs")));
        } catch (ModuleLoadException e) {
            throw ServerLogger.ROOT_LOGGER.vfsNotAvailable();
        }
        final FutureServiceContainer future = new FutureServiceContainer(container);
        final ServiceTarget tracker = container.subTarget();
        final ControlledProcessState processState = new ControlledProcessState(true);
        shutdownHook.setControlledProcessState(processState);
        ProcessStateNotifier processStateNotifier = ControlledProcessStateService.addService(tracker, processState);
        final SuspendController suspendController = new SuspendController();
        //Instantiating the suspendcontroller here to be able to get a reference to it in RunningStateJmx
        //Note that the SuspendController service will be started in the ServerService during the boot of the server.
        RunningStateJmx.registerMBean(
                processStateNotifier, suspendController,
                configuration.getRunningModeControl(),
                configuration.getServerEnvironment().getLaunchType() != ServerEnvironment.LaunchType.APPCLIENT);
        final Service<?> applicationServerService = new ApplicationServerService(extraServices, configuration, processState, suspendController);
        tracker.addService(Services.JBOSS_AS, applicationServerService)
            .install();
        final ServiceController<?> rootService = container.getRequiredService(Services.JBOSS_AS);
        rootService.addListener(new LifecycleListener() {
            @Override
            public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                switch (event) {
                    case UP: {
                        controller.removeListener(this);
                        final ServiceController<?> controllerServiceController = controller.getServiceContainer().getRequiredService(Services.JBOSS_SERVER_CONTROLLER);
                        controllerServiceController.addListener(new LifecycleListener() {
                            @Override
                            public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                                switch (event) {
                                    case UP: {
                                        future.done();
                                        controller.removeListener(this);
                                        break;
                                    }
                                    case FAILED: {
                                        future.failed(controller.getStartException());
                                        controller.removeListener(this);
                                        break;
                                    }
                                    case REMOVED: {
                                        future.failed(ServerLogger.ROOT_LOGGER.serverControllerServiceRemoved());
                                        controller.removeListener(this);
                                        break;
                                    }
                                }
                            }
                        });
                        break;
                    }
                    case FAILED: {
                        controller.removeListener(this);
                        future.failed(controller.getStartException());
                        break;
                    }
                    case REMOVED: {
                        controller.removeListener(this);
                        future.failed(ServerLogger.ROOT_LOGGER.rootServiceRemoved());
                        break;
                    }
                }
            }
        });
        return future;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AsyncFuture<ServiceContainer> startup(Configuration configuration, List<ServiceActivator> extraServices) {
        try {
            ServiceContainer container = bootstrap(configuration, extraServices).get();
            ServiceController<?> controller = container.getRequiredService(Services.JBOSS_AS);
            return (AsyncFuture<ServiceContainer>) controller.getValue();
        } catch (Exception ex) {
            shutdownHook.shutdown(true);
            throw ServerLogger.ROOT_LOGGER.cannotStartServer(ex);
        }
    }

    @Override
    public void failed() {
        shutdownHook.shutdown(true);
    }

    static class FutureServiceContainer extends AsyncFutureTask<ServiceContainer> {
        private final ServiceContainer container;
        FutureServiceContainer(final ServiceContainer container) {
            super(JBossExecutors.directExecutor());
            this.container = container;
        }

        @Override
        public void asyncCancel(final boolean interruptionDesired) {
            container.shutdown();
            container.addTerminateListener(new ServiceContainer.TerminateListener() {
                @Override
                public void handleTermination(final Info info) {
                    setCancelled();
                }
            });
        }

        void done() {
            setResult(container);
        }

        /**
         * @param t – the cause of failure, if null a generic IllegalStateException will be set.
         */
        void failed(final Throwable t) {
            Throwable cause = t != null ? t : ServerLogger.ROOT_LOGGER.throwableIsNull();
            setFailed(cause);
        }
    }

    private static class ShutdownHook extends Thread {
        private boolean down;
        private ControlledProcessState processState;
        private ServiceContainer container;

        private ServiceContainer register() {

            Runtime.getRuntime().addShutdownHook(this);
            synchronized (this) {
                if (!down) {
                    container = ServiceContainer.Factory.create("jboss-as", MAX_THREADS, 30, TimeUnit.SECONDS, false);
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
            shutdown(false);
        }

        private void shutdown(boolean failed) {
            final ServiceContainer sc;
            final ControlledProcessState ps;
            synchronized (this) {
                down = true;
                sc = container;
                ps = processState;
            }
            try {
                if (ps != null) {
                    if (!failed && ps.getState() == ControlledProcessState.State.RUNNING) {
                        suspend(sc);
                    }
                    ps.setStopping();
                }
            } finally {
                if (sc != null && !sc.isShutdownComplete()) {
                    if (!failed) {
                        // TODO this is probably better before the 'suspend' logging but the
                        // shutdown mgmt op has this order of logging
                        SystemExiter.logBeforeExit(ServerLogger.ROOT_LOGGER::shutdownHookInvoked);
                    }
                    final CountDownLatch terminateLatch = new CountDownLatch(1);
                    sc.addTerminateListener(new ServiceContainer.TerminateListener() {
                        @Override
                        public void handleTermination(Info info) {
                            terminateLatch.countDown();
                        }
                    });
                    sc.shutdown();
                    // wait for all services to finish.
                    for (;;) {
                        try {
                            terminateLatch.await();
                            break;
                        } catch (InterruptedException e) {
                            // ignored
                        }
                    }
                }
            }
        }

        private static void suspend(ServiceContainer sc) {
            try {
                final SuspendController suspendController = getSuspendController(sc);
                if (suspendController != null) {
                    long timeout = getSuspendTimeout();
                    final CountDownLatch suspendLatch = new CountDownLatch(1);
                    OperationListener listener = new OperationListener() {
                        @Override
                        public void suspendStarted() {

                        }

                        @Override
                        public void complete() {
                            suspendController.removeListener(this);
                            suspendLatch.countDown();
                        }

                        @Override
                        public void cancelled() {
                            suspendController.removeListener(this);
                            suspendLatch.countDown();
                        }

                        @Override
                        public void timeout() {
                            suspendController.removeListener(this);
                            suspendLatch.countDown();
                        }
                    };
                    suspendController.addListener(listener);
                    suspendController.suspend(timeout);
                    if (timeout > 0) {
                        // The latch should trip within 'timeout' but if necessary we'll wait
                        // 500 ms longer for it in the off chance a gc or something delays things
                        suspendLatch.await(timeout + 500, TimeUnit.MILLISECONDS);
                    } else if (timeout < 0) {
                        suspendLatch.await();
                    } // else 0 means we don't wait for tasks to finish
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            catch (Throwable t) {
                // ignored
            }
        }

        private static SuspendController getSuspendController(ServiceContainer sc) {
            SuspendController result = null;
            if (sc != null && !sc.isShutdownComplete()) {
                final ServiceController serviceController = sc.getService(JBOSS_SUSPEND_CONTROLLER);
                if (serviceController != null && serviceController.getState() == ServiceController.State.UP) {
                    result = (SuspendController) serviceController.getValue();
                }
            }
            return result;
        }

        private static long getSuspendTimeout() {
            long result = 0;
            String timeoutString = System.getProperty(SIGTERM_SUSPEND_TIMEOUT_PROP);
            if (timeoutString != null && timeoutString.length() > 0) {
                try {
                    int max = Integer.decode(timeoutString);
                    result = max > 0 ? max * 1000 : max;
                } catch(NumberFormatException ex) {
                    try {
                        ServerLogger.ROOT_LOGGER.failedToParseCommandLineInteger(SIGTERM_SUSPEND_TIMEOUT_PROP, timeoutString);
                    } catch (Throwable ignored) {
                        // ignore
                    }
                }
            }
            return result;
        }
    }
}
