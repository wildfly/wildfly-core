/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import static java.lang.System.getProperties;
import static java.lang.System.getenv;
import static java.security.AccessController.doPrivileged;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.DelegatingModelControllerClient;
import org.jboss.as.server.Bootstrap;
import org.jboss.as.server.Main;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerService;
import org.jboss.as.server.SystemExiter;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.core.jar.runtime._private.BootableJarLogger;

/**
 * Bootable jar server. Inspired from Embedded Server API standalone server.
 *
 * @author jdenise
 */
final class Server {

    interface ShutdownHandler {

        void shutdown(int status);
    }

    private static final String MODULE_ID_VFS = "org.jboss.vfs";
    private final PropertyChangeListener processStateListener;
    private final String[] cmdargs;
    private final Properties systemProps;
    private final Map<String, String> systemEnv;
    private final ModuleLoader moduleLoader;
    private ServiceContainer serviceContainer;
    private ControlledProcessState.State currentProcessState;
    private ModelControllerClient modelControllerClient;
    private ExecutorService executorService;
    private ProcessStateNotifier processStateNotifier;
    private final ShutdownHandler shutdownHandler;

    private final AtomicReference<ModelControllerClientFactory> clientFactorySvcCaptureRef;
    private final AtomicReference<ProcessStateNotifier> notifierRef;

    private Server(String[] cmdargs, Properties systemProps,
            Map<String, String> systemEnv, ModuleLoader moduleLoader,
            ShutdownHandler shutdownHandler) {
        this.cmdargs = cmdargs;
        this.systemProps = systemProps;
        this.systemEnv = systemEnv;
        this.moduleLoader = moduleLoader;
        this.shutdownHandler = shutdownHandler;
        this.clientFactorySvcCaptureRef = new AtomicReference<>();
        this.notifierRef = new AtomicReference<>();

        processStateListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if ("currentState".equals(evt.getPropertyName())) {
                    ControlledProcessState.State newState = (ControlledProcessState.State) evt.getNewValue();
                    establishModelControllerClient(newState, false);
                }
            }
        };
    }

    static Server newSever(String[] cmdargs, ModuleLoader moduleLoader, ShutdownHandler shutdownHandler) {
        setupVfsModule(moduleLoader);
        Properties sysprops = getSystemPropertiesPrivileged();
        Map<String, String> sysenv = getSystemEnvironmentPrivileged();
        return new Server(cmdargs, sysprops, sysenv, moduleLoader, shutdownHandler);
    }

    private static void setupVfsModule(final ModuleLoader moduleLoader) {
        final Module vfsModule;
        try {
            vfsModule = moduleLoader.loadModule(MODULE_ID_VFS);
        } catch (final ModuleLoadException mle) {
            throw BootableJarLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_VFS, moduleLoader);
        }
        Module.registerURLStreamHandlerFactoryModule(vfsModule);
    }

    private static Map<String, String> getSystemEnvironmentPrivileged() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return getenv();
        }
        return doPrivileged((PrivilegedAction<Map<String, String>>) System::getenv);
    }

    private static Properties getSystemPropertiesPrivileged() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return getProperties();
        }
        return doPrivileged((PrivilegedAction<Properties>) System::getProperties);
    }

    synchronized ModelControllerClient getModelControllerClient() {
        return modelControllerClient == null ? null : new DelegatingModelControllerClient(new DelegatingModelControllerClient.DelegateProvider() {
            @Override
            public ModelControllerClient getDelegate() {
                return getActiveModelControllerClient();
            }
        });
    }

    void start() throws Exception {
        Bootstrap bootstrap = null;
        try {
            final long startTime = System.currentTimeMillis();

            // Take control of server use of System.exit
            // In order to control jbossHome cleanup being done after server stop.
            SystemExiter.initialize(new SystemExiter.Exiter() {
                @Override
                public void exit(int status) {
                    Server.this.exit();
                    shutdownHandler.shutdown(status);
                }
            });

            // Determine the ServerEnvironment
            ServerEnvironment serverEnvironment = Main.determineEnvironment(cmdargs, systemProps, systemEnv, ServerEnvironment.LaunchType.STANDALONE, startTime).getServerEnvironment();
            if (serverEnvironment == null) {
                // Nothing to do
                return;
            }
            bootstrap = Bootstrap.Factory.newInstance();

            Bootstrap.Configuration configuration = new Bootstrap.Configuration(serverEnvironment);

            configuration.setModuleLoader(moduleLoader);

            // As part of bootstrap install a service to capture the ProcessStateNotifier
            ServiceActivator notifierCapture = ctx -> captureNotifier(ctx, notifierRef, ControlledProcessStateService.INTERNAL_SERVICE_NAME);
            ServiceActivator clientFactorySvcCapture = ctx -> captureNotifier(ctx, clientFactorySvcCaptureRef, ServerService.JBOSS_SERVER_CLIENT_FACTORY);

            Future<ServiceContainer> future = bootstrap.startup(configuration, List.of(notifierCapture, clientFactorySvcCapture));

            serviceContainer = future.get();

            executorService = Executors.newCachedThreadPool();

            processStateNotifier = notifierRef.get();
            processStateNotifier.addPropertyChangeListener(processStateListener);
            establishModelControllerClient(processStateNotifier.getCurrentState(), true);

        } catch (RuntimeException rte) {
            if (bootstrap != null) {
                bootstrap.failed();
            }
            throw rte;
        } catch (Exception ex) {
            if (bootstrap != null) {
                bootstrap.failed();
            }
            throw BootableJarLogger.ROOT_LOGGER.cannotStartServer(ex);
        }
    }

    private void exit() {

        if (serviceContainer != null) {
            try {
                serviceContainer.shutdown();

                serviceContainer.awaitTermination();
            } catch (RuntimeException rte) {
                throw rte;
            } catch (InterruptedException ite) {
                BootableJarLogger.ROOT_LOGGER.error(ite.getLocalizedMessage(), ite);
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                BootableJarLogger.ROOT_LOGGER.error(ex.getLocalizedMessage(), ex);
            }
        }
        if (processStateNotifier != null) {
            processStateNotifier.removePropertyChangeListener(processStateListener);
            processStateNotifier = null;
        }
        if (executorService != null) {
            try {
                executorService.shutdown();

                // 10 secs is arbitrary, but if the service container is terminated,
                // no good can happen from waiting for ModelControllerClient requests to complete
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (RuntimeException rte) {
                throw rte;
            } catch (InterruptedException ite) {
                BootableJarLogger.ROOT_LOGGER.error(ite.getLocalizedMessage(), ite);
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                BootableJarLogger.ROOT_LOGGER.error(ex.getLocalizedMessage(), ex);
            }
        }

        SystemExiter.initialize(SystemExiter.Exiter.DEFAULT);
    }

    private synchronized void establishModelControllerClient(ControlledProcessState.State state, boolean storeState) {
        modelControllerClient = null;
        if (state != ControlledProcessState.State.STOPPING && state != ControlledProcessState.State.STOPPED
                && serviceContainer != null && clientFactorySvcCaptureRef.get() != null) {
                modelControllerClient = clientFactorySvcCaptureRef.get().createSuperUserClient(executorService, true);
        }
        if (storeState || currentProcessState == null) {
            currentProcessState = state;
        }
    }

    private synchronized ModelControllerClient getActiveModelControllerClient() {
        switch (currentProcessState) {
            case STOPPING: {
                throw BootableJarLogger.ROOT_LOGGER.processIsStopping();
            }
            case STOPPED: {
                throw BootableJarLogger.ROOT_LOGGER.processIsStopped();
            }
            case STARTING:
            case RUNNING: {
                if (modelControllerClient == null) {
                    // Service wasn't available when we got the ControlledProcessState
                    // state change notification; try again
                    establishModelControllerClient(currentProcessState, false);
                    if (modelControllerClient == null) {
                        throw BootableJarLogger.ROOT_LOGGER.processIsReloading();
                    }
                }
                // fall through
            }
            default: {
                return modelControllerClient;
            }
        }
    }

    private static<T> void captureNotifier(ServiceActivatorContext ctx, AtomicReference<T> notifierRef, ServiceName requiredService) {
        ServiceBuilder<?> sb = ctx.getServiceTarget().addService();
        final Supplier<T> result = sb.requires(requiredService);
        sb.setInstance(new Service() {
            @Override
            public void start(StartContext context) {
                notifierRef.set(result.get());
            }

            @Override
            public void stop(StopContext context) {
                notifierRef.set(null);
            }
        });

        sb.setInitialMode(ServiceController.Mode.PASSIVE)
                .install();
    }
}
