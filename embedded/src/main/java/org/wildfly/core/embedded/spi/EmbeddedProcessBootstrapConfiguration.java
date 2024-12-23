/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded.spi;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import org.jboss.modules.ModuleLoader;

/**
 * Configuration information for {@link EmbeddedProcessBootstrap#startup(EmbeddedProcessBootstrapConfiguration) starting}
 * a {@link BootstrappedEmbeddedProcess}.
 */
public final class EmbeddedProcessBootstrapConfiguration {

    private final String[] cmdArgs;
    private volatile ModuleLoader moduleLoader;
    private final Consumer<Integer> systemExitCallback;

    private volatile Properties systemProperties;

    private volatile Map<String, String> systemEnv;

    private volatile File jbossHome;

    /**
     * Create a new @code EmbeddedProcessBootstrapConfiguration}.
     * @param cmdArgs arguments to pass to the process, analogous to what would be passed from the command line
     *                to a {@code main} method. Cannot be {@code null}.
     * @param systemExitCallback function to call if a system exit is being handled. The consumer is
     *                           passed the exit code. Cannot be {@code null}.
     */
    public EmbeddedProcessBootstrapConfiguration(String[] cmdArgs, Consumer<Integer> systemExitCallback) {
        this.cmdArgs = cmdArgs;
        this.systemExitCallback = systemExitCallback;
    }

    /**
     * Gets the arguments to pass to the process, analogous to what would be passed from the command line
     * to a {@code main} method.
     *
     * @return the arguments. Will not be {@code null}.
     */
    public String[] getCmdArgs() {
        return cmdArgs;
    }

    /**
     * Gets the module loader to provide to the process bootstrap.
     * @return the module loader. May be {@code null}.
     */
    public ModuleLoader getModuleLoader() {
        return moduleLoader;
    }

    /**
     * Gets the consumer to invoke if a system exit is being processed.
     * @return the consumer. Will not be {@code null}
     */
    public Consumer<Integer> getSystemExitCallback() {
        return systemExitCallback;
    }

    /**
     * Gets the properties to pass to the embedded process as if they were system properties.
     * @return the properties. May be {@code null}.
     */
    public Properties getSystemProperties() {
        return systemProperties;
    }

    /**
     * Sets the module loader to provide to the process bootstrap.
     * @param moduleLoader the module loader. May be {@code null}.
     */
    public void setModuleLoader(ModuleLoader moduleLoader) {
        this.moduleLoader = moduleLoader;
    }

    /**
     * Sets the properties to pass to the embedded process as if they were system properties.
     * @param systemProperties the properties. May be {@code null}.
     */
    public void setSystemProperties(Properties systemProperties) {
        this.systemProperties = systemProperties;
    }

    /**
     * Gets the environment values to pass to the embedded process.
     * @return the properties. May be {@code null}.
     */
    public Map<String, String> getSystemEnv() {
        return systemEnv;
    }

    /**
     * Sets the environment values to pass to the embedded process.
     * @param systemEnv the properties. May be {@code null}.
     */
    public void setSystemEnv(Map<String, String> systemEnv) {
        this.systemEnv = systemEnv;
    }

    /**
     * Gets the file to use as the root dir of the managed process.
     * @return the root dir. May be {@code null}.
     */
    public File getJBossHome() {
        return jbossHome;
    }

    /**
     * Sets the file to use as the root dir of the managed process.
     * @param jbossHome the root dir. May be {@code null}.
     */
    public void setJBossHome(File jbossHome) {
        this.jbossHome = jbossHome;
    }
}
