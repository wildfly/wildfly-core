/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.DelegatingModelControllerClient;
import org.wildfly.core.embedded.logging.EmbeddedLogger;
import org.wildfly.core.embedded.spi.BootstrappedEmbeddedProcess;
import org.wildfly.core.embedded.spi.EmbeddedModelControllerClientFactory;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrap;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrapConfiguration;
import org.wildfly.core.embedded.spi.EmbeddedProcessState;
import org.wildfly.core.embedded.spi.ProcessStateNotifier;

/**
 * Base class for modular-classloader-side implementations of {@link StandaloneServer} and {@link HostController}.
 */
abstract class AbstractEmbeddedManagedProcess implements EmbeddedManagedProcess {

    private final EmbeddedProcessBootstrap.Type type;
    private final PropertyChangeListener processStateListener;
    private final String[] cmdargs;
    private final ClassLoader embeddedModuleCL;
    private BootstrappedEmbeddedProcess embeddedProcess;
    private ModelControllerClient modelControllerClient;
    private ExecutorService executorService;

    AbstractEmbeddedManagedProcess(EmbeddedProcessBootstrap.Type type, String[] cmdargs, ClassLoader embeddedModuleCL) {
        this.type = type;
        this.cmdargs = cmdargs;
        this.embeddedModuleCL = embeddedModuleCL;

        processStateListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                establishModelControllerClient();
            }
        };
    }

    @Override
    public final synchronized ModelControllerClient getModelControllerClient() {
        return modelControllerClient == null ? null : new DelegatingModelControllerClient(this::getActiveModelControllerClient);
    }

    @Override
    public final void start() throws EmbeddedProcessStartException {
        EmbeddedProcessBootstrapConfiguration bootstrapConfiguration = getBootstrapConfiguration();

        ClassLoader tccl = SecurityActions.getTccl();
        try {
            SecurityActions.setTccl(embeddedModuleCL);
            try {

                EmbeddedProcessBootstrap bootstrap = loadEmbeddedProcessBootstrap();
                embeddedProcess = bootstrap.startup(bootstrapConfiguration);
                if (embeddedProcess == null) {
                    // bootstrapConfiguration.getCmdArgs() must have wanted --help or --version or the like
                    return;
                }

                executorService = Executors.newCachedThreadPool();

                ProcessStateNotifier processStateNotifier = embeddedProcess.getProcessStateNotifier();
                processStateNotifier.addProcessStateListener(processStateListener);
                establishModelControllerClient();

            } catch (RuntimeException rte) {
                throw rte;
            } catch (Exception ex) {
                throw EmbeddedLogger.ROOT_LOGGER.cannotStartEmbeddedServer(ex);
            }
        } finally {
            SecurityActions.setTccl(tccl);
        }
    }

    @Override
    public final void stop() {
        ClassLoader tccl = SecurityActions.getTccl();
        try {
            SecurityActions.setTccl(embeddedModuleCL);
            exit();
        } finally {
            SecurityActions.setTccl(tccl);
        }
    }

    @Override
    public String getProcessState() {
        if (embeddedProcess == null) {
            return null;
        }
        return embeddedProcess.getProcessStateNotifier().getEmbeddedProcessState().toString();
    }

    @Override
    public boolean canQueryProcessState() {
        return true;
    }

    EmbeddedProcessBootstrapConfiguration getBootstrapConfiguration() {
        return new EmbeddedProcessBootstrapConfiguration(
                cmdargs,
                (status) -> AbstractEmbeddedManagedProcess.this.exit()
        );
    }

    private EmbeddedProcessBootstrap loadEmbeddedProcessBootstrap() {
        ServiceLoader<EmbeddedProcessBootstrap> loader = ServiceLoader.load(EmbeddedProcessBootstrap.class, embeddedModuleCL);
        for (EmbeddedProcessBootstrap epb : loader) {
            if (type == epb.getType()) {
                return epb;
            }
        }
        throw new IllegalStateException();
    }

    private synchronized void establishModelControllerClient() {
        assert embeddedProcess != null;
        EmbeddedProcessState eps = embeddedProcess.getProcessStateNotifier().getEmbeddedProcessState();
        ModelControllerClient newClient = null;
        if (eps != EmbeddedProcessState.STOPPING && eps != EmbeddedProcessState.STOPPED) {
            EmbeddedModelControllerClientFactory clientFactory = embeddedProcess.getModelControllerClientFactory();
            if (clientFactory != null) {
                newClient = clientFactory.createEmbeddedClient(executorService);
            }
        }
        modelControllerClient = newClient;
    }

    private synchronized ModelControllerClient getActiveModelControllerClient() {
        assert embeddedProcess != null; // We only get called after an initial client is established
        switch (embeddedProcess.getProcessStateNotifier().getEmbeddedProcessState()) {
            case STOPPING: {
                throw EmbeddedLogger.ROOT_LOGGER.processIsStopping();
            }
            case STOPPED: {
                throw EmbeddedLogger.ROOT_LOGGER.processIsStopped();
            }
            case STARTING:
            case RUNNING: {
                if (modelControllerClient == null) {
                    // Service wasn't available when we got the ControlledProcessState
                    // state change notification; try again
                    establishModelControllerClient();
                    if (modelControllerClient == null) {
                        throw EmbeddedLogger.ROOT_LOGGER.processIsReloading();
                    }
                }
                // fall through
            }
            default: {
                return modelControllerClient;
            }
        }

    }

    private void exit() {

        if (embeddedProcess != null) {
            try {
                embeddedProcess.getServiceContainer().shutdown();

                embeddedProcess.getServiceContainer().awaitTermination();
            } catch (RuntimeException rte) {
                throw rte;
            } catch (InterruptedException ite) {
                EmbeddedLogger.ROOT_LOGGER.error(ite.getLocalizedMessage(), ite);
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                EmbeddedLogger.ROOT_LOGGER.error(ex.getLocalizedMessage(), ex);
            }

            embeddedProcess.getProcessStateNotifier().removeProcessStateListener(processStateListener);
            embeddedProcess = null;
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
                EmbeddedLogger.ROOT_LOGGER.error(ite.getLocalizedMessage(), ite);
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                EmbeddedLogger.ROOT_LOGGER.error(ex.getLocalizedMessage(), ex);
            }
        }


        if (embeddedProcess != null) {
            embeddedProcess.close();
            embeddedProcess = null;
        }
    }
}
