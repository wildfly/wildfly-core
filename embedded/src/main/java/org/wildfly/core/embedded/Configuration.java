/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.embedded;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * Represents a configuration for the embedded server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface Configuration {

    /**
     * Hints which logger provider should be used.
     */
    enum LoggerHint {
        DEFAULT(null, null),
        // Don't set the system package for the JBoss Log Manager. This causes some loggers to be set on the log context
        // from the logging subsystem as opposed to the log context created by the user.
        JBOSS_LOG_MANAGER("jboss", null),
        LOG4J("log4j", "org.apache.log4j"),
        LOG4J2("log4j2", "org.apache.logging.log4j"),
        LOGBACK("slf4j", "org.slf4j"),
        JUL("jdk", null);

        private final String providerCode;
        private final String systemPackage;

        LoggerHint(final String providerCode, final String systemPackage) {
            this.providerCode = providerCode;
            this.systemPackage = systemPackage;
        }

        /**
         * The provider code for the JBoss Logging provider.
         *
         * @return the provider code
         */
        public String getProviderCode() {
            return providerCode;
        }
    }

    /**
     * The path to the servers directory.
     *
     * @return the servers directory
     */
    Path getJBossHome();

    /**
     * The module loader to use.
     *
     * @return the module loader
     */
    ModuleLoader getModuleLoader();

    /**
     * An array of boot arguments.
     *
     * @return the boot arguments or an empty array
     */
    String[] getCommandArguments();


    /**
     * A builder for creating the {@linkplain Configuration configuration}
     */
    @SuppressWarnings({"WeakerAccess", "unused", "UnusedReturnValue"})
    class Builder {
        private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
        private static final String SYSPROP_KEY_JBOSS_MODULES_DIR = "jboss.modules.dir";
        private static final String SYSPROP_KEY_LOGGING_PROVIDER = "org.jboss.logging.provider";
        private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
        private static final String SYSPROP_KEY_SYSTEM_MODULES = "jboss.modules.system.pkgs";

        private static final String JBOSS_MODULES_DIR_NAME = "modules";

        private static final AtomicBoolean MODULE_LOADER_CONFIGURED = new AtomicBoolean(false);

        private final Path jbossHome;
        private final List<String> cmdArgs;
        private final List<String> systemPackages;
        private LoggerHint loggerHint;
        private ModuleLoader moduleLoader;
        private String modulePath;

        /**
         * Creates a new builder for the configuration.
         *
         * @param jbossHome the servers home directory
         */
        private Builder(final File jbossHome) {
            this(jbossHome.toPath());
        }

        /**
         * Creates a new builder for the configuration.
         *
         * @param jbossHome the servers home directory
         */
        private Builder(final Path jbossHome) {
            this.jbossHome = jbossHome;
            cmdArgs = new ArrayList<>();
            systemPackages = new ArrayList<>();
        }

        /**
         * Creates a new builder for the configuration.
         *
         * @param jbossHome the servers home directory
         */
        public static Builder of(final File jbossHome) {
            if (jbossHome == null) {
                throw EmbeddedLogger.ROOT_LOGGER.nullVar("jbossHome");
            }
            return new Builder(jbossHome);
        }

        /**
         * Creates a new builder for the configuration.
         *
         * @param jbossHome the servers home directory
         */
        public static Builder of(final Path jbossHome) {
            if (jbossHome == null) {
                throw EmbeddedLogger.ROOT_LOGGER.nullVar("jbossHome");
            }
            return new Builder(jbossHome);
        }

        /**
         * Adds a command argument.
         *
         * @param arg the argument to add, if {@code null} the argument is ignored
         *
         * @return this builder
         */
        public Builder addCommandArgument(final String arg) {
            if (arg != null) {
                cmdArgs.add(arg);
            }
            return this;
        }

        /**
         * Adds the arguments to the command line arguments,
         *
         * @param args the arguments to add, if {@code null} the argument is ignored
         *
         * @return this builder
         */
        public Builder addCommandArguments(final String... args) {
            if (args != null) {
                cmdArgs.addAll(Arrays.asList(args));
            }
            return this;
        }

        /**
         * Sets the command line arguments replacing any previously set arguments.
         *
         * @param args the arguments to set, if {@code null} the arguments are cleared
         *
         * @return this builder
         */
        public Builder setCommandArguments(final String... args) {
            cmdArgs.clear();
            return addCommandArguments(args);
        }

        /**
         * Sets a hint for the JBoss Logging facade on which log manager the loggers so delegate to.
         * <p>
         * Sets the {@code org.jboss.logging.provider} system property and adds the hinted logging module to the list
         * of system packages.
         * </p>
         *
         * @param loggerHint the logger hint
         *
         * @return this builder
         */
        public Builder setLoggerHint(final LoggerHint loggerHint) {
            this.loggerHint = loggerHint;
            return this;
        }

        /**
         * Sets the module loader. If the value is {@code null} a default module loader will be configured and used.
         *
         * @param moduleLoader the module loader or {@code null} to use the default
         *
         * @return this builder
         */
        public Builder setModuleLoader(final ModuleLoader moduleLoader) {
            this.moduleLoader = moduleLoader;
            return this;
        }

        /**
         * Sets the path to the modules for the server. If {@code null} the system property {@code module.path} will
         * be used if present. If the property is not present the modules directory will be determined from the servers
         * {@linkplain #getJBossHome() home directory}.
         * <p>
         * Note that this value is only used if the {@linkplain #setModuleLoader(ModuleLoader) module loader} is
         * {@code null}. Also note that once the module loader is created changing this property, even for new
         * configurations, will have no effect in the same JVM.
         * </p>
         *
         * @param modulePath the module path
         *
         * @return this builder
         */
        public Builder setModulePath(final String modulePath) {
            if (MODULE_LOADER_CONFIGURED.get()) {
                EmbeddedLogger.ROOT_LOGGER.moduleLoaderAlreadyConfigured(SYSPROP_KEY_MODULE_PATH);
            }
            this.modulePath = modulePath;
            return this;
        }

        /**
         * Adds a system package for the module loader.
         * <p>
         * Note that this value is only used if the {@linkplain #setModuleLoader(ModuleLoader) module loader} is
         * {@code null}. Also note that once the module loader is created changing this property, even for new
         * configurations, will have no effect in the same JVM.
         * </p>
         *
         * @param systemPackage the system package to add, {@code null} values are ignored
         *
         * @return this builder
         */
        public Builder addSystemPackage(final String systemPackage) {
            if (MODULE_LOADER_CONFIGURED.get()) {
                EmbeddedLogger.ROOT_LOGGER.moduleLoaderAlreadyConfigured(SYSPROP_KEY_SYSTEM_MODULES);
            }
            if (systemPackage != null) {
                this.systemPackages.add(systemPackage);
            }
            return this;
        }

        /**
         * Adds the system packages for the module loader.
         * <p>
         * Note that this value is only used if the {@linkplain #setModuleLoader(ModuleLoader) module loader} is
         * {@code null}. Also note that once the module loader is created changing this property, even for new
         * configurations, will have no effect in the same JVM.
         * </p>
         *
         * @param systemPackages the system packages to add, {@code null} values are ignored
         *
         * @return this builder
         */
        public Builder addSystemPackages(final String... systemPackages) {
            if (MODULE_LOADER_CONFIGURED.get()) {
                EmbeddedLogger.ROOT_LOGGER.moduleLoaderAlreadyConfigured(SYSPROP_KEY_SYSTEM_MODULES);
            }
            if (systemPackages != null) {
                this.systemPackages.addAll(Arrays.asList(systemPackages));
            }
            return this;
        }

        /**
         * Sets the system packages for the module loader.
         * <p>
         * Note that this value is only used if the {@linkplain #setModuleLoader(ModuleLoader) module loader} is
         * {@code null}. Also note that once the module loader is created changing this property, even for new
         * configurations, will have no effect in the same JVM.
         * </p>
         *
         * @param systemPackages the system package to add, {@code null} clears the previously set system packages
         *
         * @return this builder
         */
        public Builder setSystemPackages(final String... systemPackages) {
            if (MODULE_LOADER_CONFIGURED.get()) {
                EmbeddedLogger.ROOT_LOGGER.moduleLoaderAlreadyConfigured(SYSPROP_KEY_SYSTEM_MODULES);
            }
            this.systemPackages.clear();
            return addSystemPackages(systemPackages);
        }

        /**
         * Creates a new immutable configuration.
         *
         * @return the configuration
         */
        public Configuration build() {
            final Path jbossHome = this.jbossHome;
            final LoggerHint loggerHint = this.loggerHint == null ? LoggerHint.DEFAULT : this.loggerHint;
            configureLogging(loggerHint);
            final String[] cmdArgs = this.cmdArgs.toArray(new String[0]);
            final String[] systemPackages = this.systemPackages.toArray(new String[0]);
            final ModuleLoader moduleLoader;
            if (this.moduleLoader == null) {
                final String modulePath;
                if (this.modulePath == null) {
                    modulePath = SecurityActions.getPropertyPrivileged(SYSPROP_KEY_MODULE_PATH,
                            jbossHome.resolve(JBOSS_MODULES_DIR_NAME).toAbsolutePath().toString());
                } else {
                    modulePath = this.modulePath;
                }
                moduleLoader = setupModuleLoader(modulePath, systemPackages);
                MODULE_LOADER_CONFIGURED.set(true);
            } else {
                moduleLoader = this.moduleLoader;
            }
            return new Configuration() {
                @Override
                public Path getJBossHome() {
                    return jbossHome;
                }

                @Override
                public ModuleLoader getModuleLoader() {
                    return moduleLoader;
                }

                @Override
                public String[] getCommandArguments() {
                    return Arrays.copyOf(cmdArgs, cmdArgs.length);
                }
            };
        }

        private void configureLogging(final LoggerHint loggerHint) {
            final String providerCode = loggerHint.providerCode;
            if (SecurityActions.getPropertyPrivileged(SYSPROP_KEY_LOGGING_PROVIDER) == null && providerCode != null) {
                SecurityActions.setPropertyPrivileged(SYSPROP_KEY_LOGGING_PROVIDER, providerCode);
            }
            final String systemPackage = loggerHint.systemPackage;
            if (systemPackage != null && !systemPackages.contains(systemPackage)) {
                systemPackages.add(systemPackage);
            }
        }

        private static String trimPathToModulesDir(String modulePath) {
            int index = modulePath.indexOf(File.pathSeparator);
            return index == -1 ? modulePath : modulePath.substring(0, index);
        }

        private static ModuleLoader setupModuleLoader(final String modulePath, final String... systemPackages) {

            assert modulePath != null : "modulePath not null";

            // verify the the first element of the supplied modules path exists, and if it does not, stop and allow the user to correct.
            // Once modules are initialized and loaded we can't change Module.BOOT_MODULE_LOADER (yet).

            final Path moduleDir = Paths.get(trimPathToModulesDir(modulePath));
            if (Files.notExists(moduleDir) || !Files.isDirectory(moduleDir)) {
                throw new RuntimeException("The first directory of the specified module path " + modulePath + " is invalid or does not exist.");
            }

            // deprecated property
            SecurityActions.setPropertyPrivileged(SYSPROP_KEY_JBOSS_MODULES_DIR, moduleDir.toAbsolutePath().toString());

            final String classPath = SecurityActions.getPropertyPrivileged(SYSPROP_KEY_CLASS_PATH);
            try {
                // Set up sysprop env
                SecurityActions.clearPropertyPrivileged(SYSPROP_KEY_CLASS_PATH);
                SecurityActions.setPropertyPrivileged(SYSPROP_KEY_MODULE_PATH, modulePath);

                final StringBuilder packages = new StringBuilder("org.jboss.modules,org.jboss.dmr,org.jboss.threads,org.jboss.as.controller.client");
                if (systemPackages != null) {
                    for (String packageName : systemPackages) {
                        packages.append(",");
                        packages.append(packageName);
                    }
                }
                SecurityActions.setPropertyPrivileged(SYSPROP_KEY_SYSTEM_MODULES, packages.toString());

                // Get the module loader
                return Module.getBootModuleLoader();
            } finally {
                // Return to previous state for classpath prop
                if (classPath != null) {
                    SecurityActions.setPropertyPrivileged(SYSPROP_KEY_CLASS_PATH, classPath);
                }
            }
        }
    }

}
