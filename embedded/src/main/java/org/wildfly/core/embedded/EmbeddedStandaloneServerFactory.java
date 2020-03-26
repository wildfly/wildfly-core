/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.DelegatingModelControllerClient;
import org.jboss.as.server.Bootstrap;
import org.jboss.as.server.Main;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerService;
import org.jboss.as.server.SystemExiter;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.value.Value;
import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * This is the counter-part of EmbeddedServerFactory which lives behind a module class loader.
 * <p>
 * ServerFactory that sets up a standalone server using modular classloading.
 * </p>
 * <p>
 * To use this class the <code>jboss.home.dir</code> system property must be set to the
 * application server home directory. By default it will use the directories
 * <code>{$jboss.home.dir}/standalone/config</code> as the <i>configuration</i> directory and
 * <code>{$jboss.home.dir}/standalone/data</code> as the <i>data</i> directory. This can be overridden
 * with the <code>${jboss.server.base.dir}</code>, <code>${jboss.server.config.dir}</code> or <code>${jboss.server.config.dir}</code>
 * system properties as for normal server startup.
 * </p>
 * <p>
 * If a clean run is wanted, you can specify <code>${jboss.embedded.root}</code> to an existing directory
 * which will copy the contents of the data and configuration directories under a temporary folder. This
 * has the effect of this run not polluting later runs of the embedded server.
 * </p>
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Thomas.Diesler@jboss.com
 * @see EmbeddedProcessFactory
 */
public class EmbeddedStandaloneServerFactory {

    public static final String JBOSS_EMBEDDED_ROOT = "jboss.embedded.root";

    private EmbeddedStandaloneServerFactory() {
    }

    public static StandaloneServer create(final File jbossHomeDir, final ModuleLoader moduleLoader, final Properties systemProps, final Map<String, String> systemEnv, final String[] cmdargs, final ClassLoader embeddedModuleCL) {
        if (jbossHomeDir == null)
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("jbossHomeDir");
        if (moduleLoader == null)
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("moduleLoader");
        if (systemProps == null)
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("systemProps");
        if (systemEnv == null)
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("systemEnv");
        if (cmdargs == null)
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("cmdargs");
        if (embeddedModuleCL == null)
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("embeddedModuleCL");

        setupCleanDirectories(jbossHomeDir.toPath(), systemProps);

        return new StandaloneServerImpl(cmdargs, systemProps, systemEnv, moduleLoader, embeddedModuleCL);
    }

    static void setupCleanDirectories(Path jbossHomeDir, Properties props) {
        Path tempRoot = getTempRoot(props);
        if (tempRoot == null) {
            return;
        }

        File originalConfigDir = getFileUnderAsRoot(jbossHomeDir.toFile(), props, ServerEnvironment.SERVER_CONFIG_DIR, "configuration", true);
        File originalDataDir = getFileUnderAsRoot(jbossHomeDir.toFile(), props, ServerEnvironment.SERVER_DATA_DIR, "data", false);

        try {
            Path configDir = tempRoot.resolve("config");
            Files.createDirectory(configDir);
            Path dataDir = tempRoot.resolve("data");
            Files.createDirectory(dataDir);
            // For jboss.server.deployment.scanner.default
            Path deploymentsDir = tempRoot.resolve("deployments");
            Files.createDirectory(deploymentsDir);

            copyDirectory(originalConfigDir, configDir.toFile());
            if (originalDataDir.exists()) {
                copyDirectory(originalDataDir, dataDir.toFile());
            }

            props.put(ServerEnvironment.SERVER_BASE_DIR, tempRoot.toAbsolutePath().toString());
            props.put(ServerEnvironment.SERVER_CONFIG_DIR, configDir.toAbsolutePath().toString());
            props.put(ServerEnvironment.SERVER_DATA_DIR, dataDir.toAbsolutePath().toString());
        }  catch (IOException e) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotSetupEmbeddedServer(e);
        }

    }

    private static File getFileUnderAsRoot(File jbossHomeDir, Properties props, String propName, String relativeLocation, boolean mustExist) {
        String prop = props.getProperty(propName, null);
        if (prop == null) {
            prop = props.getProperty(ServerEnvironment.SERVER_BASE_DIR, null);
            if (prop == null) {
                File dir = new File(jbossHomeDir, "standalone" + File.separator + relativeLocation);
                if (mustExist && (!dir.exists() || !dir.isDirectory())) {
                    throw EmbeddedLogger.ROOT_LOGGER.embeddedServerDirectoryNotFound("standalone" + File.separator + relativeLocation, jbossHomeDir.getAbsolutePath());
                }
                return dir;
            } else {
                File server = new File(prop);
                validateDirectory(ServerEnvironment.SERVER_BASE_DIR, server);
                return new File(server, relativeLocation);
            }
        } else {
            File dir = new File(prop);
            validateDirectory(ServerEnvironment.SERVER_CONFIG_DIR, dir);
            return dir;
        }

    }

    private static Path getTempRoot(Properties props) {
        String tempRoot = props.getProperty(JBOSS_EMBEDDED_ROOT, null);
        if (tempRoot == null) {
            return null;
        }

        try {
            File root = new File(tempRoot);
            if (!root.exists()) {
                //Attempt to try to create the directory, in case something like target/embedded was specified
                Files.createDirectories(root.toPath());
            }
            validateDirectory("jboss.test.clean.root", root);
            return Files.createTempDirectory(root.toPath(),"configs");//let OS handle the temp creation
        } catch (IOException e) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotSetupEmbeddedServer(e);
        }
    }

    private static void validateDirectory(String property, File file) {
        if (!file.exists()) {
            throw EmbeddedLogger.ROOT_LOGGER.propertySpecifiedFileDoesNotExist(property, file.getAbsolutePath());
        }
        if (!file.isDirectory()) {
            throw EmbeddedLogger.ROOT_LOGGER.propertySpecifiedFileIsNotADirectory(property, file.getAbsolutePath());
        }
    }

    private static void copyDirectory(File src, File dest) {
        if (src.list() != null) {
            for (String current : src.list()) {
                final File srcFile = new File(src, current);
                final File destFile = new File(dest, current);

                try {
                    Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    if (srcFile.isDirectory()) {
                        copyDirectory(srcFile, destFile);
                    }
                } catch (IOException e) {
                    throw EmbeddedLogger.ROOT_LOGGER.errorCopyingFile(srcFile.getAbsolutePath(), destFile.getAbsolutePath(), e);
                }
            }
        }
    }


    private static class StandaloneServerImpl implements StandaloneServer {

        private final PropertyChangeListener processStateListener;
        private final String[] cmdargs;
        private final Properties systemProps;
        private final Map<String, String> systemEnv;
        private final ModuleLoader moduleLoader;
        private final ClassLoader embeddedModuleCL;
        private ServiceContainer serviceContainer;
        private volatile ControlledProcessState.State currentProcessState;
        private ModelControllerClient modelControllerClient;
        private ExecutorService executorService;
        private ProcessStateNotifier processStateNotifier;

        public StandaloneServerImpl(String[] cmdargs, Properties systemProps, Map<String, String> systemEnv, ModuleLoader moduleLoader, ClassLoader embeddedModuleCL) {
            this.cmdargs = cmdargs;
            this.systemProps = systemProps;
            this.systemEnv = systemEnv;
            this.moduleLoader = moduleLoader;
            this.embeddedModuleCL = embeddedModuleCL;

            processStateListener = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("currentState".equals(evt.getPropertyName())) {
                        ControlledProcessState.State newState = (ControlledProcessState.State) evt.getNewValue();
                        establishModelControllerClient(newState, true);
                    }
                }
            };
        }

        @Override
        public synchronized ModelControllerClient getModelControllerClient() {
            return modelControllerClient == null ? null : new DelegatingModelControllerClient(new DelegatingModelControllerClient.DelegateProvider() {
                @Override
                public ModelControllerClient getDelegate() {
                    return getActiveModelControllerClient();
                }
            });
        }

        @Override
        public void start() throws EmbeddedProcessStartException {
            ClassLoader tccl = EmbeddedManagedProcess.getTccl();
            try {
                EmbeddedManagedProcess.setTccl(embeddedModuleCL);
                Bootstrap bootstrap = null;
                try {
                    final long startTime = System.currentTimeMillis();

                    // Take control of server use of System.exit
                    SystemExiter.initialize(new SystemExiter.Exiter() {
                        @Override
                        public void exit(int status) {
                            StandaloneServerImpl.this.exit();
                        }
                    });

                    // Determine the ServerEnvironment
                    ServerEnvironment serverEnvironment = Main.determineEnvironment(cmdargs, systemProps, systemEnv, ServerEnvironment.LaunchType.EMBEDDED, startTime).getServerEnvironment();
                    if (serverEnvironment == null) {
                        // Nothing to do
                        return;
                    }
                    bootstrap = Bootstrap.Factory.newInstance();

                    Bootstrap.Configuration configuration = new Bootstrap.Configuration(serverEnvironment);

                    /*
                     * This would setup an {@link TransientConfigurationPersister} which does not persist anything
                     *
                    final ExtensionRegistry extensionRegistry = configuration.getExtensionRegistry();
                    final Bootstrap.ConfigurationPersisterFactory configurationPersisterFactory = new Bootstrap.ConfigurationPersisterFactory() {
                        @Override
                        public ExtensibleConfigurationPersister createConfigurationPersister(ServerEnvironment serverEnvironment, ExecutorService executorService) {
                            final QName rootElement = new QName(Namespace.CURRENT.getUriString(), "server");
                            final StandaloneXml parser = new StandaloneXml(Module.getBootModuleLoader(), executorService, extensionRegistry);
                            final File configurationFile = serverEnvironment.getServerConfigurationFile().getBootFile();
                            XmlConfigurationPersister persister = new TransientConfigurationPersister(configurationFile, rootElement, parser, parser);
                            for (Namespace namespace : Namespace.domainValues()) {
                                if (!namespace.equals(Namespace.CURRENT)) {
                                    persister.registerAdditionalRootElement(new QName(namespace.getUriString(), "server"), parser);
                                }
                            }
                            extensionRegistry.setWriterRegistry(persister);
                            return persister;
                        }
                    };
                    configuration.setConfigurationPersisterFactory(configurationPersisterFactory);
                    */

                    configuration.setModuleLoader(moduleLoader);

                    Future<ServiceContainer> future = bootstrap.startup(configuration, Collections.<ServiceActivator>emptyList());

                    serviceContainer = future.get();

                    executorService = Executors.newCachedThreadPool();

                    @SuppressWarnings({"unchecked", "deprecation"})
                    final Value<ProcessStateNotifier> processStateNotifierValue = (Value<ProcessStateNotifier>) serviceContainer.getRequiredService(ControlledProcessStateService.SERVICE_NAME);
                    processStateNotifier = processStateNotifierValue.getValue();
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
                    throw EmbeddedLogger.ROOT_LOGGER.cannotStartEmbeddedServer(ex);
                }
            } finally {
                EmbeddedManagedProcess.setTccl(tccl);
            }
        }

        @Override
        public void stop() {
            ClassLoader tccl = EmbeddedManagedProcess.getTccl();
            try {
                EmbeddedManagedProcess.setTccl(embeddedModuleCL);
                exit();
            } finally {
                EmbeddedManagedProcess.setTccl(tccl);
            }
        }

        @Override
        public String getProcessState() {
            if (currentProcessState == null) {
                return null;
            }
            return currentProcessState.toString();
        }

        private void exit() {

            if (serviceContainer != null) {
                try {
                    serviceContainer.shutdown();

                    serviceContainer.awaitTermination();
                } catch (RuntimeException rte) {
                    throw rte;
                } catch (InterruptedException ite) {
                    EmbeddedLogger.ROOT_LOGGER.error(ite.getLocalizedMessage(), ite);
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    EmbeddedLogger.ROOT_LOGGER.error(ex.getLocalizedMessage(), ex);
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
                    EmbeddedLogger.ROOT_LOGGER.error(ite.getLocalizedMessage(), ite);
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    EmbeddedLogger.ROOT_LOGGER.error(ex.getLocalizedMessage(), ex);
                }
            }


            SystemExiter.initialize(SystemExiter.Exiter.DEFAULT);
        }

        private synchronized void establishModelControllerClient(ControlledProcessState.State state, boolean storeState) {
            ModelControllerClient newClient = null;
            if (state != ControlledProcessState.State.STOPPING && state != ControlledProcessState.State.STOPPED && serviceContainer != null) {
                ModelControllerClientFactory clientFactory;
                try {
                    @SuppressWarnings("unchecked")
                    final ServiceController clientFactorySvc =
                            serviceContainer.getService(ServerService.JBOSS_SERVER_CLIENT_FACTORY);
                    clientFactory = (ModelControllerClientFactory) clientFactorySvc.getValue();
                } catch (RuntimeException e) {
                    // Either NPE because clientFactorySvc was not installed, or ISE from getValue because not UP
                    clientFactory = null;
                }
                if (clientFactory != null) {
                    newClient = clientFactory.createSuperUserClient(executorService, true);
                }
            }
            modelControllerClient = newClient;
            if (storeState || currentProcessState == null) {
                currentProcessState = state;
            }
        }

        private synchronized ModelControllerClient getActiveModelControllerClient() {
            switch (currentProcessState) {
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
                        establishModelControllerClient(currentProcessState, false);
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
    }
}
