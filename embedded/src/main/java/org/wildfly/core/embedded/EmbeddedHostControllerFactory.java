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
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.DelegatingModelControllerClient;
import org.jboss.as.host.controller.DomainModelControllerService;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.process.ProcessController;
import org.jboss.as.server.FutureServiceContainer;
import org.jboss.as.server.SystemExiter;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.version.ProductConfig;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.value.Value;
import org.jboss.stdio.StdioContext;
import org.wildfly.core.embedded.logging.EmbeddedLogger;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * This is the host controller counterpart to EmbeddedServerFactory which lives behind a module class loader.
 * <p>
 * HostContollerFactory that sets up an embedded server using modular classloading.
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
 * @see EmbeddedServerFactory
 */
public class EmbeddedHostControllerFactory {

    public static final String JBOSS_EMBEDDED_ROOT = "jboss.embedded.root";

    private EmbeddedHostControllerFactory() {
    }

    public static HostController create(final File jbossHomeDir, final ModuleLoader moduleLoader, final Properties systemProps, final Map<String, String> systemEnv, final String[] cmdargs) {
        if (jbossHomeDir == null) {
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("jbossHomeDir");
        }
        if (moduleLoader == null) {
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("moduleLoader");
        }
        if (systemProps == null) {
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("systemProps");
        }
        if (systemEnv == null) {
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("systemEnv");
        }
        if (cmdargs == null) {
            throw EmbeddedLogger.ROOT_LOGGER.nullVar("cmdargs");
        }

        setupCleanDirectories(jbossHomeDir, systemProps);
        return new HostControllerImpl(jbossHomeDir, cmdargs, systemProps, systemEnv, moduleLoader);
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
        }  catch (IOException e) {
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

    private static class HostControllerImpl implements HostController {

        private final PropertyChangeListener processStateListener;
        private final String[] cmdargs;
        private final File jbossHomeDir;
        private final Properties systemProps;
        private final Map<String, String> systemEnv;
        private final ModuleLoader moduleLoader;
        private ServiceContainer serviceContainer;
        private ControlledProcessState.State currentProcessState;
        private ModelControllerClient modelControllerClient;
        private ExecutorService executorService;
        private ControlledProcessStateService controlledProcessStateService;
        private boolean uninstallStdIo;

        public HostControllerImpl(final File jbossHomeDir, String[] cmdargs, Properties systemProps, Map<String, String> systemEnv, ModuleLoader moduleLoader) {
            this.cmdargs = cmdargs;
            this.jbossHomeDir = jbossHomeDir;
            this.systemProps = systemProps;
            this.systemEnv = systemEnv;
            this.moduleLoader = moduleLoader;
            processStateListener = new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("currentState".equals(evt.getPropertyName())) {
                        ControlledProcessState.State newState = (ControlledProcessState.State) evt.getNewValue();
                        establishModelControllerClient(newState);
                    }
                }
            };
        }

        @Override
        public HostController getHostController() {
            return this;
        }

        @Override
        public void start() throws ServerStartException {
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
                // Take control of stdio
                try {
                    StdioContext.install();
                    uninstallStdIo = true;
                } catch (IllegalStateException ignored) {
                    // already installed
                }

                final byte[] authBytes = new byte[ProcessController.AUTH_BYTES_LENGTH];
                new Random(new SecureRandom().nextLong()).nextBytes(authBytes);
                final String authCode = Base64.getEncoder().encodeToString(authBytes);

                // Determine the ServerEnvironment
                HostControllerEnvironment environment = createHostControllerEnvironment(jbossHomeDir, cmdargs, authCode, startTime);
                FutureServiceContainer futureContainer = new FutureServiceContainer();
                hostControllerBootstrap = new EmbeddedHostControllerBootstrap(futureContainer, environment, authCode);
                hostControllerBootstrap.bootstrap();
                serviceContainer = futureContainer.get();
                executorService = Executors.newCachedThreadPool();
                @SuppressWarnings("unchecked")
                final Value<ControlledProcessStateService> processStateServiceValue = (Value<ControlledProcessStateService>) serviceContainer.getRequiredService(ControlledProcessStateService.SERVICE_NAME);
                controlledProcessStateService = processStateServiceValue.getValue();
                controlledProcessStateService.addPropertyChangeListener(processStateListener);
                establishModelControllerClient(controlledProcessStateService.getCurrentState());
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

        private synchronized void establishModelControllerClient(ControlledProcessState.State state) {
            ModelControllerClient newClient = null;
            if (state != ControlledProcessState.State.STOPPING && serviceContainer != null) {
                @SuppressWarnings("unchecked")
                final ServiceController<ModelController> modelControllerValue = (ServiceController<ModelController>) serviceContainer.getService(DomainModelControllerService.SERVICE_NAME);
                if (modelControllerValue != null) {
                    final ModelController controller = modelControllerValue.getValue();
                    newClient = controller.createClient(executorService);
                }
            }
            modelControllerClient = newClient;
            currentProcessState = state;
        }

        private synchronized ModelControllerClient getActiveModelControllerClient() {
            switch (currentProcessState) {
                case STOPPING: {
                    throw EmbeddedLogger.ROOT_LOGGER.processIsStopping();
                }
                case STARTING: {
                    if (modelControllerClient == null) {
                        // Service wasn't available when we got the ControlledProcessState
                        // state change notification; try again
                        establishModelControllerClient(currentProcessState);
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
            exit();
        }

        private void exit() {

            if (serviceContainer != null) {
                try {
                    serviceContainer.shutdown();
                    serviceContainer.awaitTermination();
                } catch (RuntimeException rte) {
                    throw rte;
                } catch (InterruptedException ite) {
                    ite.printStackTrace();
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            if (controlledProcessStateService != null) {
                controlledProcessStateService.removePropertyChangeListener(processStateListener);
                controlledProcessStateService = null;
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
                    ite.printStackTrace();
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            if (uninstallStdIo) {
                try {
                    StdioContext.uninstall();
                } catch (IllegalStateException ignored) {
                    // something else already did
                }
            }

            SystemExiter.initialize(SystemExiter.Exiter.DEFAULT);
        }

        private static Map<String, String> getHostSystemProperties() {
            final Map<String, String> hostSystemProperties = new HashMap<String, String>();
            try {
                RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
                for (String arg : runtime.getInputArguments()) {
                    if (arg != null && arg.length() > 2 && arg.startsWith("-D")) {
                        arg = arg.substring(2);
                        String[] split = arg.split("=");
                        if (!hostSystemProperties.containsKey(split[0])) {
                            String val;
                            if (split.length == 1) {
                                val = null;
                            } else if (split.length == 2) {
                                val = split[1];
                            } else {
                                //Things like -Djava.security.policy==/Users/kabir/tmp/permit.policy will end up here, and the extra '=' needs to be part of the value,
                                //see http://docs.oracle.com/javase/6/docs/technotes/guides/security/PolicyFiles.html
                                StringBuilder sb = new StringBuilder();
                                for (int i = 2 ; i < split.length ; i++) {
                                    sb.append("=");
                                }
                                sb.append(split[split.length - 1]);
                                val = sb.toString();
                            }
                            hostSystemProperties.put(split[0], val);
                        }
                    }
                }
            } catch (Exception e) {
                EmbeddedLogger.ROOT_LOGGER.cannotSetupEmbeddedServer(e);
            }
            return hostSystemProperties;
        }

        private static HostControllerEnvironment createHostControllerEnvironment(File jbossHome, String[] cmdargs, String hostName, long startTime) {
            try {
                // for SecurityActions.getSystemProperty("jboss.home.dir") in InstallationManagerService
                System.setProperty("jboss.home.dir", jbossHome.getAbsolutePath());
                WildFlySecurityManager.setPropertyPrivileged("jboss.home.dir", jbossHome.getAbsolutePath());

                Map<String, String> props = new HashMap<String, String>();
                props.put(HostControllerEnvironment.HOME_DIR, jbossHome.getAbsolutePath());

                File domain = new File(jbossHome, "domain");
                props.put(HostControllerEnvironment.DOMAIN_BASE_DIR, domain.getAbsolutePath());

                File configuration = new File(domain, "configuration");
                props.put(HostControllerEnvironment.DOMAIN_CONFIG_DIR, configuration.getAbsolutePath());

                props.put(HostControllerEnvironment.HOST_NAME, hostName);

                String domainConfig = null;
                String hostConfig = null;
                for(int i=0; i<cmdargs.length; i++) {
                    try {
                        final String arg = cmdargs[i];
                        if (CommandLineConstants.DOMAIN_CONFIG.equals(arg) || CommandLineConstants.SHORT_DOMAIN_CONFIG.equals(arg)) {
                            domainConfig = cmdargs[++i];
                        } else if (CommandLineConstants.HOST_CONFIG.equals(arg)) {
                            hostConfig = cmdargs[++i];
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new RuntimeException(e);
                    }
                }

                boolean isRestart = false;
                String modulePath = "";

                // these are set, but ignored.
                InetAddress processControllerAddress = InetAddress.getLocalHost();
                Integer processControllerPort = 9999;
                InetAddress hostControllerAddress = InetAddress.getLocalHost();
                Integer hostControllerPort = 9990;
                String defaultJVM = null;
                String initialDomainConfig = null;
                String initialHostConfig = null;
                Map<String, String> hostSystemProperties = getHostSystemProperties();

                // WFCORE-938
                // see also {@link org.jboss.as.cli.handlers.ReloadHandler} for ADMIN_ONLY being forced
                RunningMode initialRunningMode = RunningMode.ADMIN_ONLY;
                boolean backupDomainFiles = false;
                boolean useCachedDc = false;
                ProductConfig productConfig = new ProductConfig(Module.getBootModuleLoader(), WildFlySecurityManager.getPropertyPrivileged(HostControllerEnvironment.HOME_DIR, jbossHome.getAbsolutePath()), hostSystemProperties);
                return new HostControllerEnvironment(props, isRestart, modulePath, processControllerAddress, processControllerPort,
                        hostControllerAddress, hostControllerPort, defaultJVM, domainConfig, initialDomainConfig, hostConfig, initialHostConfig,
                        initialRunningMode, backupDomainFiles, useCachedDc, productConfig, false, startTime, ProcessType.EMBEDDED_HOST_CONTROLLER);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
    }
}




