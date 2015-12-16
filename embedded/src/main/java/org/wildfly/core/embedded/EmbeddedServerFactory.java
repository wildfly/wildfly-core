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
 * Factory that sets up an embedded standalone server using modular classloading.
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
 */
public class EmbeddedServerFactory {

    private static final String MODULE_ID_EMBEDDED = "org.wildfly.embedded";
    private static final String MODULE_ID_LOGMANAGER = "org.jboss.logmanager";
    private static final String MODULE_ID_VFS = "org.jboss.vfs";
    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_LOGMANAGER = "java.util.logging.manager";
    private static final String SYSPROP_KEY_JBOSS_HOME_DIR = "jboss.home.dir";
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
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_DEPLOY_DIR = "jboss.domain.deploy.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR = "jboss.domain.temp.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR = "jboss.domain.log.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_DATA_DIR = "jboss.domain.data.dir";

    private static final String JBOSS_MODULES_DIR_NAME = "modules";

    // used internally to differentiate the type of embedded server, without needing to import ProcessType
    private static enum EmbeddedServerType {
        STANDALONE,
        HOST_CONTROLLER
    };

    private EmbeddedServerFactory() {
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

    public static StandaloneServer create(String jbossHomePath, String modulePath, String... systemPackages) {
       return create(jbossHomePath, modulePath, systemPackages, null);
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
     *
     * @return the server. Will not be {@code null}
     */
    public static StandaloneServer create(String jbossHomePath, String modulePath, String[] systemPackages, String[] cmdargs) {
        if (jbossHomePath == null || jbossHomePath.isEmpty()) {
            throw EmbeddedLogger.ROOT_LOGGER.invalidJBossHome(jbossHomePath);
        }
        File jbossHomeDir = new File(jbossHomePath);
        if (!jbossHomeDir.isDirectory()) {
            throw EmbeddedLogger.ROOT_LOGGER.invalidJBossHome(jbossHomePath);
        }

        if (modulePath == null)
            modulePath = jbossHomeDir.getAbsolutePath() + File.separator + JBOSS_MODULES_DIR_NAME;

        return create(setupModuleLoader(modulePath, systemPackages), jbossHomeDir, cmdargs);
    }

    public static EmbeddedServerReference createStandalone(String jbossHomePath, String modulePath, String[] systemPackages, String[] cmdargs) {
        return (EmbeddedServerReference) create(jbossHomePath, modulePath, systemPackages, cmdargs);
    }

    /**
     * Create an embedded standalone server with an already established module loader.
     *
     * @param moduleLoader the module loader. Cannot be {@code null}
     * @param jbossHomeDir the location of the root of server installation. Cannot be {@code null} or empty.
     * @return the server. Will not be {@code null}
     */
    public static StandaloneServer create(ModuleLoader moduleLoader, File jbossHomeDir) {
        return create(moduleLoader, jbossHomeDir, new String[0]);
    }

    /**
     * Create an embedded standalone server with an already established module loader.
     *
     * @param moduleLoader the module loader. Cannot be {@code null}
     * @param jbossHomeDir the location of the root of server installation. Cannot be {@code null} or empty.
     * @param cmdargs any additional arguments to pass to the embedded server (e.g. -b=192.168.100.10)
     * @return the server. Will not be {@code null}
     */
    public static StandaloneServer create(ModuleLoader moduleLoader, File jbossHomeDir, String[] cmdargs) {

        // in the case of a stop and a restart with a different jbossHomeDir, we need to reset some properties.
        // that are set in @org.jboss.as.ServerEnvironment
        resetEmbeddedServerProperties(jbossHomeDir.getAbsolutePath(), EmbeddedServerType.STANDALONE);

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
        Object standaloneServerImpl = createServer(EmbeddedServerType.STANDALONE, createServerMethod, moduleLoader, jbossHomeDir, cmdargs);
        return new EmbeddedServerReference(standaloneServerClass, standaloneServerImpl);
    }

    public static EmbeddedServerReference createStandalone(ModuleLoader moduleLoader, File jbossHomeDir, String[] cmdargs) {
        return (EmbeddedServerReference) create(moduleLoader, jbossHomeDir, cmdargs);
    }

    public static EmbeddedServerReference createHostController(String jbossHomePath, String modulePath, String[] systemPackages, String[] cmdargs) {
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
     * @param cmdargs      any additional arguments to pass to the embedded server (e.g. -b=192.168.100.10)
     * @return the running host controller Will not be {@code null}
     */
    public static EmbeddedServerReference createHostController(ModuleLoader moduleLoader, File jbossHomeDir, String[] cmdargs) {

        // reset properties if we've restarted with a changed jbossHomeDir
        resetEmbeddedServerProperties(jbossHomeDir.getAbsolutePath(), EmbeddedServerType.HOST_CONTROLLER);

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
        Object hostControllerImpl = createServer(EmbeddedServerType.HOST_CONTROLLER, createServerMethod, moduleLoader, jbossHomeDir, cmdargs);
        return new EmbeddedServerReference(hostControllerClass, hostControllerImpl);
    }

    private static String trimPathToModulesDir(String modulePath) {
        int index = modulePath.indexOf(File.pathSeparator);
        return index == -1 ? modulePath : modulePath.substring(0, index);
    }

    private static ModuleLoader setupModuleLoader(String modulePath, String... systemPackages) {

        assert modulePath != null : "modulePath not null";

        // verify the supplied modules path exists, and if it does not, stop and allow the user to correct.
        // Once modules are initialized and loaded we can't change Module.BOOT_MODULE_LOADER (yet).

        File moduleDir = new File(modulePath);
        if (!moduleDir.exists() || !moduleDir.isDirectory()) {
            throw new RuntimeException("The specified module directory " + modulePath + " is invalid or does not exist.");
        }

        WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_MODULES_DIR, trimPathToModulesDir(modulePath));

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
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_VFS, moduleLoader);
        }
        Module.registerURLStreamHandlerFactoryModule(vfsModule);
    }

    private static void setupLoggingSystem(ModuleLoader moduleLoader) {
        final ModuleIdentifier logModuleId = ModuleIdentifier.create(MODULE_ID_LOGMANAGER);
        final Module logModule;
        try {
            logModule = moduleLoader.loadModule(logModuleId);
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle,MODULE_ID_LOGMANAGER, moduleLoader);
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

    private static Object createServer(final EmbeddedServerType embeddedType, final Method createServerMethod, final ModuleLoader moduleLoader, final File jbossHomeDir, final String[] cmdargs) {
        Object serverImpl;
        try {
            Properties sysprops = WildFlySecurityManager.getSystemPropertiesPrivileged();
            Map<String, String> sysenv = WildFlySecurityManager.getSystemEnvironmentPrivileged();
            String[] args = cmdargs != null ? cmdargs : new String[0];
            serverImpl = createServerMethod.invoke(null, jbossHomeDir, moduleLoader, sysprops, sysenv, args);
        } catch (final InvocationTargetException ite) {
            if (embeddedType == EmbeddedServerType.HOST_CONTROLLER) {
                throw EmbeddedLogger.ROOT_LOGGER.cannotCreateHostController(ite.getCause(), createServerMethod);
            }
            throw EmbeddedLogger.ROOT_LOGGER.cannotCreateStandaloneServer(ite.getCause(), createServerMethod);
        } catch (final IllegalAccessException iae) {
            if (embeddedType == EmbeddedServerType.HOST_CONTROLLER) {
                throw EmbeddedLogger.ROOT_LOGGER.cannotCreateHostController(iae, createServerMethod);
            }
            throw EmbeddedLogger.ROOT_LOGGER.cannotCreateStandaloneServer(iae, createServerMethod);
        }
        return serverImpl;
    }

    private static void resetEmbeddedServerProperties(final String jbossHomeDir, final EmbeddedServerType embeddedServerType) {
        assert jbossHomeDir != null;

        String jbossBaseDir;
        WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_HOME_DIR, jbossHomeDir);
        switch (embeddedServerType) {
            case STANDALONE:
                jbossBaseDir = jbossHomeDir + File.separator + "standalone";
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_SERVER_CONFIG_DIR, jbossBaseDir + File.separator + "configuration");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_SERVER_DEPLOY_DIR, jbossBaseDir + File.separator + "data" + File.separator +"content");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_SERVER_TEMP_DIR, jbossBaseDir + File.separator + "data" + File.separator + "tmp");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_SERVER_LOG_DIR, jbossBaseDir + File.separator + "log");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_SERVER_DATA_DIR, jbossBaseDir + File.separator + "data");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_SERVER_BASE_DIR, jbossBaseDir);
                break;
            case HOST_CONTROLLER:
                jbossBaseDir = jbossHomeDir + File.separator + "domain";
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR, jbossBaseDir + File.separator + "configuration");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_DOMAIN_DEPLOY_DIR, jbossBaseDir + File.separator + "data" + File.separator +"content");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR, jbossBaseDir + File.separator + "data" + File.separator + "tmp");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR, jbossBaseDir + File.separator + "log");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_DOMAIN_DATA_DIR, jbossBaseDir + File.separator + "data");
                WildFlySecurityManager.setPropertyPrivileged(SYSPROP_KEY_JBOSS_DOMAIN_BASE_DIR, jbossBaseDir);
                break;
            default:
                throw new RuntimeException("Unknown embedded server type: " + embeddedServerType);
        }
    }

}
