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

import static java.lang.System.getProperties;
import static java.lang.System.getenv;
import static java.security.AccessController.doPrivileged;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Properties;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * <p>
 * Factory that sets up an embedded {@link StandaloneServer} or {@link HostController} process using modular classloading.
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
    private static final String MODULE_ID_VFS = "org.jboss.vfs";

    private static final String SYSPROP_KEY_JBOSS_DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_DEPLOYMENT_DIR = "jboss.domain.deployment.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR = "jboss.domain.temp.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR = "jboss.domain.log.dir";

    static final String[] DOMAIN_KEYS = {
            SYSPROP_KEY_JBOSS_DOMAIN_BASE_DIR, SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR, SYSPROP_KEY_JBOSS_DOMAIN_DEPLOYMENT_DIR,
            SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR, SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR, SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR
    };

    private static final String HOST_FACTORY = "org.wildfly.core.embedded.EmbeddedHostControllerFactory";
    private static final String SERVER_FACTORY = "org.wildfly.core.embedded.EmbeddedStandaloneServerFactory";
    /**
     * Valid types of embedded managed processes.
     */
    enum ProcessType {
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
        return createStandaloneServer(
                Configuration.Builder.of(jbossHomeDir)
                        .setModulePath(modulePath)
                        .setSystemPackages(systemPackages)
                        .setCommandArguments(cmdargs)
                        .build()
        );
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
        return createStandaloneServer(
                Configuration.Builder.of(jbossHomeDir)
                        .setCommandArguments(cmdargs)
                        .setModuleLoader(moduleLoader)
                        .build()
        );
    }

    /**
     * Create an embedded standalone server with an already established module loader.
     *
     * @param configuration the configuration for the embedded server
     * @return the running embedded server. Will not be {@code null}
     */
    public static StandaloneServer createStandaloneServer(final Configuration configuration) {
        final ChainedContext context = new ChainedContext();
        context.add(new StandaloneSystemPropertyContext(configuration.getJBossHome()));
        context.add(new LoggerContext(configuration.getModuleLoader()));
        final ModuleLoader moduleLoader = configuration.getModuleLoader();

        setupVfsModule(moduleLoader);

        // Load the Embedded Server Module
        final Module embeddedModule;
        try {
            embeddedModule = moduleLoader.loadModule(MODULE_ID_EMBEDDED);
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_EMBEDDED, moduleLoader);
        }

        // Load the Embedded Server Factory via the modular environment
        final ModuleClassLoader embeddedModuleCL = embeddedModule.getClassLoader();
        final Class<?> embeddedServerFactoryClass;
        final Class<?> standaloneServerClass;
        try {
            embeddedServerFactoryClass = embeddedModuleCL.loadClass(SERVER_FACTORY);
            standaloneServerClass = embeddedModuleCL.loadClass(StandaloneServer.class.getName());
        } catch (final ClassNotFoundException cnfe) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotLoadEmbeddedServerFactory(cnfe, SERVER_FACTORY);
        }

        // Get a handle to the method which will create the server
        boolean useClMethod = true;
        Method createServerMethod;
        try {
            createServerMethod = embeddedServerFactoryClass.getMethod("create", File.class, ModuleLoader.class, Properties.class, Map.class, String[].class, ClassLoader.class);
        } catch (final NoSuchMethodException nsme) {
            useClMethod = false;
            try {
                // Check if we're using a version of WildFly Core before 6.0 which did not include the create method that accepts a class loader
                createServerMethod = embeddedServerFactoryClass.getMethod("create", File.class, ModuleLoader.class, Properties.class, Map.class, String[].class);
            } catch (final NoSuchMethodException e) {
                throw EmbeddedLogger.ROOT_LOGGER.cannotGetReflectiveMethod(e, "create", embeddedServerFactoryClass.getName());
            }
        }
        // Create the server
        Object standaloneServerImpl = createManagedProcess(ProcessType.STANDALONE_SERVER, createServerMethod, configuration, useClMethod ? embeddedModuleCL : null);
        return new EmbeddedManagedProcessImpl(standaloneServerClass, standaloneServerImpl, context);
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
        return createHostController(
                Configuration.Builder.of(jbossHomeDir)
                        .setModulePath(modulePath)
                        .setSystemPackages(systemPackages)
                        .setCommandArguments(cmdargs)
                        .build()
        );
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
        return createHostController(
                Configuration.Builder.of(jbossHomeDir)
                        .setModuleLoader(moduleLoader)
                        .setCommandArguments(cmdargs)
                        .build()
        );
    }

    /**
     * Create an embedded host controller with an already established module loader.
     *
     * @param configuration the configuration for the embedded host controller
     * @return the running host controller Will not be {@code null}
     */
    public static HostController createHostController(final Configuration configuration) {
        final ChainedContext context = new ChainedContext();
        context.add(new HostControllerSystemPropertyContext(configuration.getJBossHome()));
        context.add(new LoggerContext(configuration.getModuleLoader()));
        final ModuleLoader moduleLoader = configuration.getModuleLoader();

        setupVfsModule(moduleLoader);

        // Load the Embedded Server Module
        final Module embeddedModule;
        try {
            embeddedModule = moduleLoader.loadModule(MODULE_ID_EMBEDDED);
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_EMBEDDED, moduleLoader);
        }

        // Load the Embedded Server Factory via the modular environment
        final ModuleClassLoader embeddedModuleCL = embeddedModule.getClassLoader();
        final Class<?> embeddedHostControllerFactoryClass;
        final Class<?> hostControllerClass;
        try {
            embeddedHostControllerFactoryClass = embeddedModuleCL.loadClass(HOST_FACTORY);
            hostControllerClass = embeddedModuleCL.loadClass(HostController.class.getName());
        } catch (final ClassNotFoundException cnfe) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotLoadEmbeddedServerFactory(cnfe, HOST_FACTORY);
        }

        // Get a handle to the method which will create the server
        boolean useClMethod = true;
        Method createServerMethod;
        try {
            createServerMethod = embeddedHostControllerFactoryClass.getMethod("create", File.class, ModuleLoader.class, Properties.class, Map.class, String[].class, ClassLoader.class);
        } catch (final NoSuchMethodException nsme) {
            useClMethod = false;
            try {
                // Check if we're using a version of WildFly Core before 6.0 which did not include the create method that accepts a class loader
                createServerMethod = embeddedHostControllerFactoryClass.getMethod("create", File.class, ModuleLoader.class, Properties.class, Map.class, String[].class);
            } catch (final NoSuchMethodException e) {
                throw EmbeddedLogger.ROOT_LOGGER.cannotGetReflectiveMethod(e, "create", embeddedHostControllerFactoryClass.getName());
            }
        }

        // Create the server
        Object hostControllerImpl = createManagedProcess(ProcessType.HOST_CONTROLLER, createServerMethod, configuration, useClMethod ? embeddedModuleCL : null);

        Method methodGetProcessState;
        EmbeddedManagedProcessImpl embeddedManagedProcess;
        try {
            methodGetProcessState = hostControllerClass.getMethod("getProcessState");
            embeddedManagedProcess = new EmbeddedManagedProcessImpl(hostControllerClass, hostControllerImpl, context, methodGetProcessState);
        } catch (final NoSuchMethodException nsme) {
            embeddedManagedProcess = new EmbeddedManagedProcessImpl(hostControllerClass, hostControllerImpl, context);
        }

        return embeddedManagedProcess;
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

    private static Object createManagedProcess(final ProcessType embeddedType, final Method createServerMethod, final Configuration configuration, ModuleClassLoader embeddedModuleCL) {
        Object serverImpl;
        try {
            Properties sysprops = getSystemPropertiesPrivileged();
            Map<String, String> sysenv = getSystemEnvironmentPrivileged();
            String[] args = configuration.getCommandArguments();
            if (embeddedModuleCL == null) {
                serverImpl = createServerMethod.invoke(null, configuration.getJBossHome().toFile(), configuration.getModuleLoader(), sysprops, sysenv, args);
            } else {
                serverImpl = createServerMethod.invoke(null, configuration.getJBossHome().toFile(), configuration.getModuleLoader(), sysprops, sysenv, args, embeddedModuleCL);
            }
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

    private static Properties getSystemPropertiesPrivileged() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return getProperties();
        }
        return doPrivileged((PrivilegedAction<Properties>) System::getProperties);
    }

    private static Map<String, String> getSystemEnvironmentPrivileged() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return getenv();
        }
        return doPrivileged((PrivilegedAction<Map<String, String>>) System::getenv);
    }
}
