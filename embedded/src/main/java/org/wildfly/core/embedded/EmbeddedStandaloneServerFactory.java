/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

import org.jboss.modules.ModuleLoader;
import org.wildfly.core.embedded.logging.EmbeddedLogger;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrap;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrapConfiguration;

/**
 * This is the standalone server counter-part of EmbeddedProcessFactory that lives behind a module class loader.
 * <p>
 * Factory that sets up an embedded @{link StandaloneServer} using modular classloading.
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
    private static final String SERVER_BASE_DIR = "jboss.server.base.dir";
    private static final String SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String SERVER_DATA_DIR = "jboss.server.data.dir";

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

        File originalConfigDir = getFileUnderAsRoot(jbossHomeDir.toFile(), props, SERVER_CONFIG_DIR, "configuration", true);
        File originalDataDir = getFileUnderAsRoot(jbossHomeDir.toFile(), props, SERVER_DATA_DIR, "data", false);

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

            props.put(SERVER_BASE_DIR, tempRoot.toAbsolutePath().toString());
            props.put(SERVER_CONFIG_DIR, configDir.toAbsolutePath().toString());
            props.put(SERVER_DATA_DIR, dataDir.toAbsolutePath().toString());
        }  catch (IOException e) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotSetupEmbeddedServer(e);
        }

    }

    private static File getFileUnderAsRoot(File jbossHomeDir, Properties props, String propName, String relativeLocation, boolean mustExist) {
        String prop = props.getProperty(propName, null);
        if (prop == null) {
            prop = props.getProperty(SERVER_BASE_DIR, null);
            if (prop == null) {
                File dir = new File(jbossHomeDir, "standalone" + File.separator + relativeLocation);
                if (mustExist && (!dir.exists() || !dir.isDirectory())) {
                    throw EmbeddedLogger.ROOT_LOGGER.embeddedServerDirectoryNotFound("standalone" + File.separator + relativeLocation, jbossHomeDir.getAbsolutePath());
                }
                return dir;
            } else {
                File server = new File(prop);
                validateDirectory(SERVER_BASE_DIR, server);
                return new File(server, relativeLocation);
            }
        } else {
            File dir = new File(prop);
            validateDirectory(SERVER_CONFIG_DIR, dir);
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


    private static class StandaloneServerImpl extends AbstractEmbeddedManagedProcess implements StandaloneServer {

        private final Properties systemProps;
        private final Map<String, String> systemEnv;
        private final ModuleLoader moduleLoader;

        public StandaloneServerImpl(String[] cmdargs, Properties systemProps, Map<String, String> systemEnv, ModuleLoader moduleLoader, ClassLoader embeddedModuleCL) {
            super(EmbeddedProcessBootstrap.Type.STANDALONE_SERVER, cmdargs, embeddedModuleCL);
            this.systemProps = systemProps;
            this.systemEnv = systemEnv;
            this.moduleLoader = moduleLoader;
        }

        @Override
        public String getProcessState() {
            // The access to the internal process state is unsupported for the standalone embedded server, you can use a
            // management operation to query the current server process state instead.
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canQueryProcessState() {
            return false;
        }

        @Override
        EmbeddedProcessBootstrapConfiguration getBootstrapConfiguration() {
            EmbeddedProcessBootstrapConfiguration configuration = super.getBootstrapConfiguration();
            configuration.setModuleLoader(moduleLoader);
            configuration.setSystemProperties(systemProps);
            configuration.setSystemEnv(systemEnv);
            return configuration;
        }
    }

}
