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
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.DelegatingModelControllerClient;
import org.jboss.as.host.controller.DomainModelControllerService;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.Main;
import org.jboss.as.process.ProcessController;
import org.jboss.as.server.FutureServiceContainer;
import org.jboss.as.server.SystemExiter;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.value.Value;
import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * This is the host controller counterpart to EmbeddedProcessFactory which lives behind a module class loader.
 * <p>
 * Factory that sets up an embedded {@link HostController} using modular classloading.
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
 * @author Ken Wills <kwills@redhat.com>
 * @see EmbeddedProcessFactory
 */
public class EmbeddedHostControllerFactory {

    public static final String JBOSS_EMBEDDED_ROOT = "jboss.embedded.root";
    private static final String MODULE_PATH = "-mp";
    private static final String PC_ADDRESS = "--pc-address";
    private static final String PC_PORT = "--pc-port";


    private EmbeddedHostControllerFactory() {
    }

    public static HostController create(final File jbossHomeDir, final ModuleLoader moduleLoader, final Properties systemProps, final Map<String, String> systemEnv, final String[] cmdargs, ClassLoader embeddedModuleCL) {
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

        setupCleanDirectories(jbossHomeDir, systemProps);
        return new HostControllerImpl(jbossHomeDir, cmdargs, systemProps, systemEnv, moduleLoader, embeddedModuleCL);
    }

    static void setupCleanDirectories(File jbossHomeDir, Properties props) {
        File tempRoot = getTempRoot(props);
        if (tempRoot == null) {
            return;
        }

        File originalConfigDir = getFileUnderAsRoot(jbossHomeDir, props, HostControllerEnvironment.DOMAIN_CONFIG_DIR, "configuration", true);
        File originalDataDir = getFileUnderAsRoot(jbossHomeDir, props, HostControllerEnvironment.DOMAIN_DATA_DIR, "data", false);

        try {
            File configDir = new File(tempRoot, "config");
            Files.createDirectory(configDir.toPath());
            File dataDir = new File(tempRoot, "data");
            Files.createDirectory(dataDir.toPath());
            // For jboss.server.deployment.scanner.default
            File deploymentsDir = new File(tempRoot, "deployments");
            Files.createDirectory(deploymentsDir.toPath());

            copyDirectory(originalConfigDir, configDir);
            if (originalDataDir.exists()) {
                copyDirectory(originalDataDir, dataDir);
            }

            props.put(HostControllerEnvironment.DOMAIN_BASE_DIR, tempRoot.getAbsolutePath());
            props.put(HostControllerEnvironment.DOMAIN_CONFIG_DIR, configDir.getAbsolutePath());
            props.put(HostControllerEnvironment.DOMAIN_DATA_DIR, dataDir.getAbsolutePath());
        } catch (IOException e) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotSetupEmbeddedServer(e);
        }

    }

    private static File getFileUnderAsRoot(File jbossHomeDir, Properties props, String propName, String relativeLocation, boolean mustExist) {
        String prop = props.getProperty(propName, null);
        if (prop == null) {
            prop = props.getProperty(HostControllerEnvironment.DOMAIN_BASE_DIR, null);
            if (prop == null) {
                File dir = new File(jbossHomeDir, "domain" + File.separator + relativeLocation);
                if (mustExist && (!dir.exists() || !dir.isDirectory())) {
                    throw ServerLogger.ROOT_LOGGER.embeddedServerDirectoryNotFound("domain" + File.separator + relativeLocation, jbossHomeDir.getAbsolutePath());
                }
                return dir;
            } else {
                File server = new File(prop);
                validateDirectory(HostControllerEnvironment.DOMAIN_BASE_DIR, server);
                return new File(server, relativeLocation);
            }
        } else {
            File dir = new File(prop);
            validateDirectory(HostControllerEnvironment.DOMAIN_BASE_DIR, dir);
            return dir;
        }

    }

    private static File getTempRoot(Properties props) {
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
            root = new File(root, "configs");
            Files.createDirectories(root.toPath());
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            root = new File(root, format.format(new Date()));
            Files.createDirectory(root.toPath());
            return root;
        } catch (IOException e) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotSetupEmbeddedServer(e);
        }
    }

    private static void validateDirectory(String property, File file) {
        if (!file.exists()) {
            throw ServerLogger.ROOT_LOGGER.propertySpecifiedFileDoesNotExist(property, file.getAbsolutePath());
        }
        if (!file.isDirectory()) {
            throw ServerLogger.ROOT_LOGGER.propertySpecifiedFileIsNotADirectory(property, file.getAbsolutePath());
        }
    }

    private static void copyDirectory(File src, File dest) {
        if(src.list() != null) {
            for (String current : src.list()) {
                final File srcFile = new File(src, current);
                final File destFile = new File(dest, current);

                try {
                    Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    if (srcFile.isDirectory()) {
                        copyDirectory(srcFile, destFile);
                    }
                } catch (IOException e) {
                    throw ServerLogger.ROOT_LOGGER.errorCopyingFile(srcFile.getAbsolutePath(), destFile.getAbsolutePath(), e);
                }
            }
        }
    }

    private static class HostControllerImpl implements HostController {

        private final PropertyChangeListener processStateListener;
        private final String[] cmdargs;
        private final File jbossHomeDir;
        private final Properties systemProps;
        private final Map<String, String> systemEnv;
        private final ModuleLoader moduleLoader;
        private final ClassLoader embeddedModuleCL;
        private ServiceContainer serviceContainer;
        private volatile ControlledProcessState.State currentProcessState;
        private ModelControllerClient modelControllerClient;
        private ExecutorService executorService;
        private ProcessStateNotifier processStateNotifier;

        public HostControllerImpl(final File jbossHomeDir, String[] cmdargs, Properties systemProps, Map<String, String> systemEnv, ModuleLoader moduleLoader, ClassLoader embeddedModuleCL) {
            this.cmdargs = cmdargs;
            this.jbossHomeDir = jbossHomeDir;
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
        public void start() throws EmbeddedProcessStartException {
            ClassLoader tccl = SecurityActions.getTccl();
            try {
                SecurityActions.setTccl(embeddedModuleCL);
                EmbeddedHostControllerBootstrap hostControllerBootstrap = null;
                try {
                    final long startTime = System.currentTimeMillis();
                    // Take control of server use of System.exit
                    SystemExiter.initialize(new SystemExiter.Exiter() {
                        @Override
                        public void exit(int status) {
                            HostControllerImpl.this.exit();
                        }
                    });

                    // Determine the HostControllerEnvironment
                    HostControllerEnvironment environment = createHostControllerEnvironment(jbossHomeDir, cmdargs, startTime);

                    FutureServiceContainer futureContainer = new FutureServiceContainer();
                    final byte[] authBytes = new byte[ProcessController.AUTH_BYTES_LENGTH];
                    new Random(new SecureRandom().nextLong()).nextBytes(authBytes);
                    final String authCode = Base64.getEncoder().encodeToString(authBytes);
                    hostControllerBootstrap = new EmbeddedHostControllerBootstrap(futureContainer, environment, authCode);
                    hostControllerBootstrap.bootstrap(processStateListener);
                    serviceContainer = futureContainer.get();
                    executorService = Executors.newCachedThreadPool();
                    @SuppressWarnings({"unchecked", "deprecation"})
                    final Value<ProcessStateNotifier> processStateNotifierValue = (Value<ProcessStateNotifier>) serviceContainer.getRequiredService(ControlledProcessStateService.SERVICE_NAME);
                    processStateNotifier = processStateNotifierValue.getValue();
                    establishModelControllerClient(currentProcessState, false);
                } catch (RuntimeException rte) {
                    if (hostControllerBootstrap != null) {
                        hostControllerBootstrap.failed();
                    }
                    throw rte;
                } catch (Exception ex) {
                    if (hostControllerBootstrap != null) {
                        hostControllerBootstrap.failed();
                    }
                    throw EmbeddedLogger.ROOT_LOGGER.cannotStartEmbeddedServer(ex);
                }
            } finally {
                SecurityActions.setTccl(tccl);
            }
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

        private synchronized void establishModelControllerClient(ControlledProcessState.State state, boolean storeState) {
            ModelControllerClient newClient = null;
            if (state != ControlledProcessState.State.STOPPING && state != ControlledProcessState.State.STOPPED && serviceContainer != null) {
                ModelControllerClientFactory  clientFactory;
                try {
                    @SuppressWarnings("unchecked")
                    final ServiceController clientFactorySvc =
                            serviceContainer.getService(DomainModelControllerService.CLIENT_FACTORY_SERVICE_NAME);
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


        @Override
        public void stop() {
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
            if (currentProcessState == null) {
                return null;
            }
            return currentProcessState.toString();
        }

        @Override
        public boolean canQueryProcessState() {
            return true;
        }

        private void exit() {

            if (serviceContainer != null) {
                try {
                    serviceContainer.shutdown();
                    serviceContainer.awaitTermination();
                } catch (RuntimeException rte) {
                    throw rte;
                } catch (InterruptedException ite) {
                    ServerLogger.ROOT_LOGGER.error(ite.getLocalizedMessage(), ite);
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    ServerLogger.ROOT_LOGGER.error(ex.getLocalizedMessage(), ex);
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
                    ServerLogger.ROOT_LOGGER.error(ite.getLocalizedMessage(), ite);
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    ServerLogger.ROOT_LOGGER.error(ex.getLocalizedMessage(), ex);
                }
            }

            SystemExiter.initialize(SystemExiter.Exiter.DEFAULT);
        }

        private static HostControllerEnvironment createHostControllerEnvironment(File jbossHome, String[] cmdargs, long startTime) {
            SecurityActions.setPropertyPrivileged(HostControllerEnvironment.HOME_DIR, jbossHome.getAbsolutePath());

            List<String> cmds = new ArrayList<String>(Arrays.asList(cmdargs));

            // these are for compatibility with Main.determineEnvironment / HostControllerEnvironment
            // Once WFCORE-938 is resolved, --admin-only will allow a connection back to the DC for slaves,
            // and support a method for setting the domain master address outside of -Djboss.domain.primary.address
            // so we'll probably need a command line argument for this if its not specified as a system prop
            if (SecurityActions.getPropertyPrivileged(HostControllerEnvironment.JBOSS_DOMAIN_PRIMARY_ADDRESS, null) == null) {
                SecurityActions.setPropertyPrivileged(HostControllerEnvironment.JBOSS_DOMAIN_PRIMARY_ADDRESS, "127.0.0.1");
            }
            cmds.add(MODULE_PATH);
            cmds.add(SecurityActions.getPropertyPrivileged("module.path", ""));
            cmds.add(PC_ADDRESS);
            cmds.add("0");
            cmds.add(PC_PORT);
            cmds.add("0");
            // this used to be set in the embedded-hc specific env setup, WFCORE-938 will add support for --admin-only=false
            cmds.add("--admin-only");

            for (final String prop : EmbeddedProcessFactory.DOMAIN_KEYS) {
                // if we've started with any jboss.domain.base.dir etc, copy those in here.
                String value = SecurityActions.getPropertyPrivileged(prop, null);
                if (value != null)
                    cmds.add("-D" + prop + "=" + value);
            }
            return Main.determineEnvironment(cmds.toArray(new String[cmds.size()]), startTime, ProcessType.EMBEDDED_HOST_CONTROLLER).getHostControllerEnvironment();
        }


    }
}




