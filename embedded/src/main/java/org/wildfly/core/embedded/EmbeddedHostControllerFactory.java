/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.jboss.modules.ModuleLoader;
import org.wildfly.core.embedded.logging.EmbeddedLogger;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrap;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrapConfiguration;

/**
 * This is the host controller counterpart to EmbeddedProcessFactory that lives behind a module class loader.
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

    private static final String DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    private static final String DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";
    private static final String DOMAIN_DATA_DIR = "jboss.domain.data.dir";

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
        return new HostControllerImpl(jbossHomeDir, cmdargs, systemProps, systemEnv, embeddedModuleCL);
    }

    static void setupCleanDirectories(File jbossHomeDir, Properties props) {
        File tempRoot = getTempRoot(props);
        if (tempRoot == null) {
            return;
        }

        File originalConfigDir = getFileUnderAsRoot(jbossHomeDir, props, DOMAIN_CONFIG_DIR, "configuration", true);
        File originalDataDir = getFileUnderAsRoot(jbossHomeDir, props, DOMAIN_DATA_DIR, "data", false);

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

            props.put(DOMAIN_BASE_DIR, tempRoot.getAbsolutePath());
            props.put(DOMAIN_CONFIG_DIR, configDir.getAbsolutePath());
            props.put(DOMAIN_DATA_DIR, dataDir.getAbsolutePath());
        } catch (IOException e) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotSetupEmbeddedServer(e);
        }

    }

    private static File getFileUnderAsRoot(File jbossHomeDir, Properties props, String propName, String relativeLocation, boolean mustExist) {
        String prop = props.getProperty(propName, null);
        if (prop == null) {
            prop = props.getProperty(DOMAIN_BASE_DIR, null);
            if (prop == null) {
                File dir = new File(jbossHomeDir, "domain" + File.separator + relativeLocation);
                if (mustExist && (!dir.exists() || !dir.isDirectory())) {
                    throw EmbeddedLogger.ROOT_LOGGER.embeddedServerDirectoryNotFound("domain" + File.separator + relativeLocation, jbossHomeDir.getAbsolutePath());
                }
                return dir;
            } else {
                File server = new File(prop);
                validateDirectory(DOMAIN_BASE_DIR, server);
                return new File(server, relativeLocation);
            }
        } else {
            File dir = new File(prop);
            validateDirectory(DOMAIN_BASE_DIR, dir);
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
            throw EmbeddedLogger.ROOT_LOGGER.propertySpecifiedFileDoesNotExist(property, file.getAbsolutePath());
        }
        if (!file.isDirectory()) {
            throw EmbeddedLogger.ROOT_LOGGER.propertySpecifiedFileIsNotADirectory(property, file.getAbsolutePath());
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
                    throw EmbeddedLogger.ROOT_LOGGER.errorCopyingFile(srcFile.getAbsolutePath(), destFile.getAbsolutePath(), e);
                }
            }
        }
    }

    private static class HostControllerImpl extends AbstractEmbeddedManagedProcess implements HostController {

        private final File jbossHomeDir;
        private final Properties systemProps; // TODO why is this not used?
        private final Map<String, String> systemEnv; // TODO why is this not used?

        public HostControllerImpl(final File jbossHomeDir, String[] cmdargs, Properties systemProps, Map<String, String> systemEnv, ClassLoader embeddedModuleCL) {
            super(EmbeddedProcessBootstrap.Type.HOST_CONTROLLER, cmdargs, embeddedModuleCL);
            this.jbossHomeDir = jbossHomeDir;
            this.systemProps = systemProps;
            this.systemEnv = systemEnv;
        }

        @Override
        EmbeddedProcessBootstrapConfiguration getBootstrapConfiguration() {
            EmbeddedProcessBootstrapConfiguration configuration = super.getBootstrapConfiguration();
            configuration.setJBossHome(jbossHomeDir);
            return configuration;
        }

    }

}




