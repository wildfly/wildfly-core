/*
Copyright 2015 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.core.embedded;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.logging.LogManager;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.log.JDKModuleLogger;
import org.wildfly.core.embedded.logging.EmbeddedLogger;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * <p>
 * Factory that sets up an embedded server or Host Controller process using modular classloading.
 * </p>
 * <p>
 * If a clean run is wanted, you can specify <code>${jboss.embedded.root}</code> to an existing directory
 * which will copy the contents of the data and configuration directories under a temporary folder. This
 * has the effect of this run not polluting later runs of the embedded server.
 * </p>
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Thomas.Diesler@jboss.com
 */
public class EmbeddedProcessFactory {

    private static final String MODULE_ID_EMBEDDED = "org.wildfly.embedded";
    private static final String MODULE_ID_LOGMANAGER = "org.jboss.logmanager";
    private static final String MODULE_ID_VFS = "org.jboss.vfs";
    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_LOGMANAGER = "java.util.logging.manager";
    private static final String SYSPROP_KEY_JBOSS_HOME_DIR = "jboss.home.dir";
    private static final String SYSPROP_KEY_JBOSS_PREV_HOME_DIR = "jboss.prev.home.dir";
    private static final String SYSPROP_KEY_JBOSS_MODULES_DIR = "jboss.modules.dir";
    private static final String SYSPROP_VALUE_JBOSS_LOGMANAGER = "org.jboss.logmanager.LogManager";

    private static final String SYSPROP_KEY_JBOSS_SERVER_BASE_DIR = "jboss.server.base.dir";
    private static final String SYSPROP_KEY_JBOSS_SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String SYSPROP_KEY_JBOSS_SERVER_DEPLOY_DIR = "jboss.server.deploy.dir";
    private static final String SYSPROP_KEY_JBOSS_SERVER_TEMP_DIR = "jboss.server.temp.dir";
    private static final String SYSPROP_KEY_JBOSS_SERVER_LOG_DIR = "jboss.server.log.dir";
    private static final String SYSPROP_KEY_JBOSS_SERVER_DATA_DIR = "jboss.server.data.dir";

    private static final String SYSPROP_KEY_JBOSS_DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_DEPLOYMENT_DIR = "jboss.domain.deployment.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR = "jboss.domain.temp.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR = "jboss.domain.log.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_DATA_DIR = "jboss.domain.data.dir";
    private static final String SYSPROP_KEY_JBOSS_CONTROLLER_BASE_DIR = "jboss.server.controller.base.dir";

    // these properties set in the CLI when starting -Dfoo.bar=quux and passed as the to the embedded process creation.
    static final String[] STANDALONE_KEYS = {
            SYSPROP_KEY_JBOSS_SERVER_BASE_DIR, SYSPROP_KEY_JBOSS_SERVER_CONFIG_DIR, SYSPROP_KEY_JBOSS_SERVER_DEPLOY_DIR,
            SYSPROP_KEY_JBOSS_SERVER_TEMP_DIR, SYSPROP_KEY_JBOSS_SERVER_LOG_DIR, SYSPROP_KEY_JBOSS_SERVER_DATA_DIR
    };

    static final String[] DOMAIN_KEYS = {
            SYSPROP_KEY_JBOSS_DOMAIN_BASE_DIR, SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR, SYSPROP_KEY_JBOSS_DOMAIN_DEPLOYMENT_DIR,
            SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR, SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR, SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR
    };

    private static final String JBOSS_MODULES_DIR_NAME = "modules";

    /**
     * Valid types of embedded managed processes.
     */
    private enum ProcessType {
        STANDALONE_SERVER,
        HOST_CONTROLLER
    }

    EmbeddedProcessFactory() {
    }

    /**
     * Create an embedded standalone server.
     *
     * @param jbossHomePath the location of the root of server installation. Cannot be {@code null} or empty.
     * @param modulePath the location of the root of the module repository. May be {@code null} if the standard
     *                   location under {@code jbossHomePath} should be used
     * @param systemPackages names of any packages that must be treated as system packages, with the same classes
     *                       visible to the caller's classloader visible to server-side classes loaded from
     *                       the server's modular classloader
     * @return the server. Will not be {@code null}
     */
    public static StandaloneServer createStandaloneServer(String jbossHomePath, String modulePath, String... systemPackages) {
        return createStandaloneServer(jbossHomePath, modulePath, systemPackages, null);
    }

    /**
     * Create an embedded standalone server.
     *
     * @param jbossHomePath the location of the root of server installation. Cannot be {@code null} or empty.
     * @param modulePath the location of the root of the module repository. May be {@code null} if the standard
     *                   location under {@code jbossHomePath} should be used
     * @param systemPackages names of any packages that must be treated as system packages, with the same classes
     *                       visible to the caller's classloader visible to server-side classes loaded from
     *                       the server's modular classloader
     * @param cmdargs any additional arguments to pass to the embedded server (e.g. -b=192.168.100.10)
     * @return the server. Will not be {@code null}
     */
    public static StandaloneServer createStandaloneServer(String jbossHomePath, String modulePath, String[] systemPackages, String[] cmdargs) {
        if (jbossHomePath == null || jbossHomePath.isEmpty()) {
            throw EmbeddedLogger.ROOT_LOGGER.invalidJBossHome(jbossHomePath);
        }
        File jbossHomeDir = new File(jbossHomePath);
        if (!jbossHomeDir.isDirectory()) {
            throw EmbeddedLogger.ROOT_LOGGER.invalidJBossHome(jbossHomePath);
        }

        if (modulePath == null)
            modulePath = jbossHomeDir.getAbsolutePath() + File.separator + JBOSS_MODULES_DIR_NAME;

        return createStandaloneServer(setupModuleLoader(modulePath, systemPackages), jbossHomeDir, cmdargs);
    }

    /**
     * Create an embedded standalone server with an already established module loader.
     *
     * @param moduleLoader the module loader. Cannot be {@code null}
     * @param jbossHomeDir the location of the root of server installation. Cannot be {@code null} or empty.
     * @param cmdargs      any additional arguments to pass to the embedded server (e.g. -b=192.168.100.10)
     * @return the running embedded server. Will not be {@code null}
     */
    public static StandaloneServer createStandaloneServer(ModuleLoader moduleLoader, File jbossHomeDir, String... cmdargs) {

        // in the case of a stop and a restart with a different jbossHomeDir, we need to reset some properties.
        // that are set in @org.jboss.as.ServerEnvironment
        resetEmbeddedServerProperties(jbossHomeDir.getAbsolutePath(), ProcessType.STANDALONE_SERVER);

        setupVfsModule(moduleLoader);
        setupLoggingSystem(moduleLoader);

        // Load the Embedded Server Module
        final Module embeddedModule;
        try {
            embeddedModule = moduleLoader.loadModule(ModuleIdentifier.create(MODULE_ID_EMBEDDED));
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_EMBEDDED, moduleLoader);
        }

        // Load the Embedded Server Factory via the modular environment
        final ModuleClassLoader embeddedModuleCL = embeddedModule.getClassLoader();
        final Class<?> embeddedServerFactoryClass;
        final Class<?> standaloneServerClass;
        try {
            embeddedServerFactoryClass = embeddedModuleCL.loadClass(EmbeddedStandaloneServerFactory.class.getName());
            standaloneServerClass = embeddedModuleCL.loadClass(StandaloneServer.class.getName());
        } catch (final ClassNotFoundException cnfe) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotLoadEmbeddedServerFactory(cnfe, EmbeddedStandaloneServerFactory.class.getName());
        }

        // Get a handle to the method which will create the server
        final Method createServerMethod;
        try {
            createServerMethod = embeddedServerFactoryClass.getMethod("create", File.class, ModuleLoader.class, Properties.class, Map.class, String[].class);
        } catch (final NoSuchMethodException nsme) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotGetReflectiveMethod(nsme, "create", embeddedServerFactoryClass.getName());
        }
        // Create the server
        Object standaloneServerImpl = createManagedProcess(ProcessType.STANDALONE_SERVER, createServerMethod, moduleLoader, jbossHomeDir, cmdargs);
        return new EmbeddedManagedProcessImpl(standaloneServerClass, standaloneServerImpl);
    }

    /**
     * Create an embedded host controller.
     *
     * @param jbossHomePath the location of the root of the host controller installation. Cannot be {@code null} or empty.
     * @param modulePath the location of the root of the module repository. May be {@code null} if the standard
     *                   location under {@code jbossHomePath} should be used
     * @param systemPackages names of any packages that must be treated as system packages, with the same classes
     *                       visible to the caller's classloader visible to host-controller-side classes loaded from
     *                       the server's modular classloader
     * @param cmdargs any additional arguments to pass to the embedded host controller (e.g. -b=192.168.100.10)
     * @return the server. Will not be {@code null}
     */
    public static HostController createHostController(String jbossHomePath, String modulePath, String[] systemPackages, String[] cmdargs) {
        if (jbossHomePath == null || jbossHomePath.isEmpty()) {
            throw EmbeddedLogger.ROOT_LOGGER.invalidJBossHome(jbossHomePath);
        }
        File jbossHomeDir = new File(jbossHomePath);
        if (!jbossHomeDir.isDirectory()) {
            throw EmbeddedLogger.ROOT_LOGGER.invalidJBossHome(jbossHomePath);
        }

        if (modulePath == null)
            modulePath = jbossHomeDir.getAbsolutePath() + File.separator + JBOSS_MODULES_DIR_NAME;

        return createHostController(setupModuleLoader(modulePath, systemPackages), jbossHomeDir, cmdargs);
    }

    /**
     * Create an embedded host controller with an already established module loader.
     *
     * @param moduleLoader the module loader. Cannot be {@code null}
     * @param jbossHomeDir the location of the root of server installation. Cannot be {@code null} or empty.
     * @param cmdargs      any additional arguments to pass to the embedded host controller (e.g. -b=192.168.100.10)
     * @return the running host controller Will not be {@code null}
     */
    public static HostController createHostController(ModuleLoader moduleLoader, File jbossHomeDir, String[] cmdargs) {

        // reset properties if we've restarted with a changed jbossHomeDir
        resetEmbeddedServerProperties(jbossHomeDir.getAbsolutePath(), ProcessType.HOST_CONTROLLER);

        setupVfsModule(moduleLoader);
        setupLoggingSystem(moduleLoader);

        // Load the Embedded Server Module
        final Module embeddedModule;
        try {
            embeddedModule = moduleLoader.loadModule(ModuleIdentifier.create(MODULE_ID_EMBEDDED));
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_EMBEDDED, moduleLoader);
        }

        // Load the Embedded Server Factory via the modular environment
        final ModuleClassLoader embeddedModuleCL = embeddedModule.getClassLoader();
        final Class<?> embeddedHostControllerFactoryClass;
        final Class<?> hostControllerClass;
        try {
            embeddedHostControllerFactoryClass = embeddedModuleCL.loadClass(EmbeddedHostControllerFactory.class.getName());
            hostControllerClass = embeddedModuleCL.loadClass(HostController.class.getName());
        } catch (final ClassNotFoundException cnfe) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotLoadEmbeddedServerFactory(cnfe, EmbeddedHostControllerFactory.class.getName());
        }

        // Get a handle to the method which will create the server
        final Method createServerMethod;
        try {
            createServerMethod = embeddedHostControllerFactoryClass.getMethod("create", File.class, ModuleLoader.class, Properties.class, Map.class, String[].class);
        } catch (final NoSuchMethodException nsme) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotGetReflectiveMethod(nsme, "create", embeddedHostControllerFactoryClass.getName());
        }

        // Create the server
        Object hostControllerImpl = createManagedProcess(ProcessType.HOST_CONTROLLER, createServerMethod, moduleLoader, jbossHomeDir, cmdargs);
        return new EmbeddedManagedProcessImpl(hostControllerClass, hostControllerImpl);
    }

    private static String trimPathToModulesDir(String modulePath) {
        int index = modulePath.indexOf(File.pathSeparator);
        return index == -1 ? modulePath : modulePath.substring(0, index);
    }

    private static ModuleLoader setupModuleLoader(String modulePath, String... systemPackages) {

        assert modulePath != null : "modulePath not null";

        // verify the the first element of the supplied modules path exists, and if it does not, stop and allow the user to correct.
        // Once modules are initialized and loaded we can't change Module.BOOT_MODULE_LOADER (yet).

        File moduleDir = new File(trimPathToModulesDir(modulePath));
        if (!moduleDir.exists() || !moduleDir.isDirectory()) {
            throw new RuntimeException("The first directory of the specified module path " + modulePath + " is invalid or does not exist.");
        }

        // deprecated property
        WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_MODULES_DIR, moduleDir.getPath());

        final String classPath = WildFlySecurityManager.getPropertyPrivileged(SYSPROP_KEY_CLASS_PATH, null);
        try {
            // Set up sysprop env
            WildFlySecurityManager.clearPropertyPrivileged(SYSPROP_KEY_CLASS_PATH);
            WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_MODULE_PATH, modulePath);

            StringBuilder packages = new StringBuilder("org.jboss.modules,org.jboss.msc,org.jboss.dmr,org.jboss.threads,org.jboss.as.controller.client");
            if (systemPackages != null) {
                for (String packageName : systemPackages) {
                    packages.append(",");
                    packages.append(packageName);
                }
            }
            WildFlySecurityManager.setPropertyPrivileged("jboss.modules.system.pkgs", packages.toString());

            // Get the module loader
            return Module.getBootModuleLoader();
        } finally {
            // Return to previous state for classpath prop
            WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_CLASS_PATH, classPath);
        }
    }

    private static void setupVfsModule(final ModuleLoader moduleLoader) {
        final ModuleIdentifier vfsModuleID = ModuleIdentifier.create(MODULE_ID_VFS);
        final Module vfsModule;
        try {
            vfsModule = moduleLoader.loadModule(vfsModuleID);
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle,MODULE_ID_VFS, moduleLoader);
        }
        Module.registerURLStreamHandlerFactoryModule(vfsModule);
    }

    private static void setupLoggingSystem(ModuleLoader moduleLoader) {
        final ModuleIdentifier logModuleId = ModuleIdentifier.create(MODULE_ID_LOGMANAGER);
        final Module logModule;
        try {
            logModule = moduleLoader.loadModule(logModuleId);
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_LOGMANAGER, moduleLoader);
        }

        final ModuleClassLoader logModuleClassLoader = logModule.getClassLoader();
        final ClassLoader tccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(logModuleClassLoader);
            WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_LOGMANAGER, SYSPROP_VALUE_JBOSS_LOGMANAGER);

            final Class<?> actualLogManagerClass = LogManager.getLogManager().getClass();
            if (actualLogManagerClass == LogManager.class) {
                System.err.println("Cannot not load JBoss LogManager. The LogManager has likely been accessed prior to this initialization.");
            } else {
                Module.setModuleLogger(new JDKModuleLogger());
            }
        } finally {
            // Reset TCCL
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(tccl);
        }
    }

    private static Object createManagedProcess(final ProcessType embeddedType, final Method createServerMethod, final ModuleLoader moduleLoader, final File jbossHomeDir, final String[] cmdargs) {
        Object serverImpl;
        try {
            Properties sysprops = WildFlySecurityManager.getSystemPropertiesPrivileged();
            Map<String, String> sysenv = WildFlySecurityManager.getSystemEnvironmentPrivileged();
            String[] args = cmdargs != null ? cmdargs : new String[0];
            serverImpl = createServerMethod.invoke(null, jbossHomeDir, moduleLoader, sysprops, sysenv, args);
        } catch (final InvocationTargetException ite) {
            if (embeddedType == ProcessType.HOST_CONTROLLER) {
                throw EmbeddedLogger.ROOT_LOGGER.cannotCreateHostController(ite.getCause(), createServerMethod);
            }
            throw EmbeddedLogger.ROOT_LOGGER.cannotCreateStandaloneServer(ite.getCause(), createServerMethod);
        } catch (final IllegalAccessException iae) {
            if (embeddedType == ProcessType.HOST_CONTROLLER) {
                throw EmbeddedLogger.ROOT_LOGGER.cannotCreateHostController(iae, createServerMethod);
            }
            throw EmbeddedLogger.ROOT_LOGGER.cannotCreateStandaloneServer(iae, createServerMethod);
        }
        return serverImpl;
    }

    private static void resetEmbeddedServerProperties(final String jbossHomeDir, final ProcessType embeddedServerType) {
        assert jbossHomeDir != null;
        String oldJbossHomeDir = WildFlySecurityManager.getPropertyPrivileged(SYSPROP_KEY_JBOSS_PREV_HOME_DIR, null);
        boolean shouldReset = oldJbossHomeDir != null && !jbossHomeDir.equals(oldJbossHomeDir);
        WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_HOME_DIR, jbossHomeDir);
        // store this for the next server start, if we need it. This avoids having to mess with the environment restorer, if we were
        // started with JBOSS_HOME etc.
        WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_PREV_HOME_DIR, jbossHomeDir);

        if (shouldReset) {
            // we only reset in non-modular when --jboss-home is changed. In case we've started with -Djboss.server.base.dir (or similar) set,
            // warn the user that these properties will be reset if --jboss-home has changed). For normal ("modular") use, jboss-home can't
            // be changed (yet) without exiting the CLI.
            EmbeddedLogger.ROOT_LOGGER.warn("JBOSS_HOME has been changed, all configuration system properties will be set to the default directories below this new JBOSS_HOME");
            resetProperties(embeddedServerType, jbossHomeDir);
        }
    }

    private static void resetProperties(final ProcessType embeddedServerType, final String jbossHomeDir) {
        assert jbossHomeDir != null;
        String jbossBaseDir;
        switch (embeddedServerType) {
            case STANDALONE_SERVER:
                jbossBaseDir = jbossHomeDir + File.separator + "standalone";
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_SERVER_BASE_DIR, jbossBaseDir);
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_SERVER_CONFIG_DIR, jbossBaseDir + File.separator + "configuration");
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_SERVER_DATA_DIR, jbossBaseDir + File.separator + "data");
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_SERVER_DEPLOY_DIR, jbossBaseDir + File.separator + "data" + File.separator + "content");
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_SERVER_TEMP_DIR, jbossBaseDir + File.separator + "data" + File.separator + "tmp");
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_SERVER_LOG_DIR, jbossBaseDir + File.separator + "log");
                break;
            case HOST_CONTROLLER:
                jbossBaseDir = jbossHomeDir + File.separator + "domain";
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_DOMAIN_BASE_DIR, jbossBaseDir);
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR, jbossBaseDir + File.separator + "configuration");
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_DOMAIN_DATA_DIR, jbossBaseDir + File.separator + "data");
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_DOMAIN_DEPLOYMENT_DIR, jbossBaseDir + File.separator + "data" + File.separator + "content");
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR, jbossBaseDir + File.separator + "data" + File.separator + "tmp");
                resetEmbeddedServerProperty(SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR, jbossBaseDir + File.separator + "log");
                break;
            default:
                throw new RuntimeException("Unknown embedded server type: " + embeddedServerType);
        }
    }

    private static void resetEmbeddedServerProperty(String propertyName, String value) {
            WildFlySecurityManager.setPropertyPrivileged(propertyName, value);
    }
}
