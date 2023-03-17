/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.management.util;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.test.shared.FileUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.junit.Assert;

/**
 * Utilities for running tests of domain mode.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DomainTestSupport implements AutoCloseable {


    private static final Logger log = Logger.getLogger("org.jboss.as.test.integration.domain");

    public static final String primaryAddress = System.getProperty("jboss.test.host.primary.address", "127.0.0.1");
    public static final String secondaryAddress = System.getProperty("jboss.test.host.secondary.address", "127.0.0.1");
    /** @deprecated unused */
    @Deprecated()
    public static final long domainBootTimeout = Long.valueOf(System.getProperty("jboss.test.domain.boot.timeout", "60000"));
    /** @deprecated unused */
    @Deprecated
    public static final long domainShutdownTimeout = Long.valueOf(System.getProperty("jboss.test.domain.shutdown.timeout", "20000"));
    @SuppressWarnings("WeakerAccess")
    public static final String primaryJvmHome = System.getProperty("jboss.test.host.primary.jvmhome");
    @SuppressWarnings("WeakerAccess")
    public static final String secondaryJvmHome = System.getProperty("jboss.test.host.secondary.jvmhome");
    @SuppressWarnings("WeakerAccess")
    public static final String primaryControllerJvmHome = System.getProperty("jboss.test.host.primary.controller.jvmhome");
    @SuppressWarnings("WeakerAccess")
    public static final String secondaryControllerJvmHome = System.getProperty("jboss.test.host.secondary.controller.jvmhome");

    /**
     * Create and start a default configuration for the domain tests.
     *
     * @param testName the test name
     * @return a started domain test support
     */
    public static DomainTestSupport createAndStartDefaultSupport(final String testName) {
        try {
            final Configuration configuration;
            if(Boolean.getBoolean("wildfly.primary.debug")) {
                 configuration = DomainTestSupport.Configuration.createDebugPrimary(testName,
                    "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary.xml");
            } else if (Boolean.getBoolean("wildfly.secondary.debug")) {
                configuration = DomainTestSupport.Configuration.createDebugSecondary(testName,
                    "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary.xml");
            } else {
                configuration = DomainTestSupport.Configuration.create(testName,
                    "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary.xml");
            }
            final DomainTestSupport testSupport = DomainTestSupport.create(configuration);
            // Start!
            testSupport.start();
            return testSupport;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create and start a configuration for the domain tests
     *
     * @param configuration the configuration specification
     * @return a started domain test support
     */
    public static DomainTestSupport createAndStartSupport(Configuration configuration) {
        try {
            final DomainTestSupport testSupport = DomainTestSupport.create(configuration);
            // Start!
            testSupport.start();
            return testSupport;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static WildFlyManagedConfiguration getPrimaryConfiguration(String domainConfigPath, String hostConfigPath,
                                                                     String testName, boolean readOnlyDomain, boolean readOnlyHost) {
        return getPrimaryConfiguration(domainConfigPath, hostConfigPath, testName, null, readOnlyDomain, readOnlyHost);
    }

    public static WildFlyManagedConfiguration getPrimaryConfiguration(String domainConfigPath, String hostConfigPath,
                String testName, WildFlyManagedConfiguration baseConfig,
                boolean readOnlyDomain, boolean readOnlyHost) {
        return getPrimaryConfiguration(domainConfigPath, hostConfigPath, testName, baseConfig, readOnlyDomain, readOnlyHost, Boolean.getBoolean("wildfly.primary.debug"));
    }

    public static WildFlyManagedConfiguration getPrimaryConfiguration(String domainConfigPath, String hostConfigPath,
                String testName, WildFlyManagedConfiguration baseConfig,
                boolean readOnlyDomain, boolean readOnlyHost, boolean debug) {
        return Configuration.getPrimaryConfiguration(domainConfigPath, hostConfigPath, testName, baseConfig, readOnlyDomain, readOnlyHost, debug);
    }
    public static WildFlyManagedConfiguration getSecondaryConfiguration(String hostConfigPath, String testName,
                                                                    boolean readOnlyHost) {
        return getSecondaryConfiguration("secondary", hostConfigPath, testName, new WildFlyManagedConfiguration(), readOnlyHost);
    }

    public static WildFlyManagedConfiguration getSecondaryConfiguration(String hostName, String hostConfigPath, String testName,
                                                                    boolean readOnlyHost) {
        return getSecondaryConfiguration(hostName, hostConfigPath, testName, new WildFlyManagedConfiguration(), readOnlyHost);
    }

    public static WildFlyManagedConfiguration getSecondaryConfiguration(String hostConfigPath, String testName,
                                                                    WildFlyManagedConfiguration baseConfig,
                                                                    boolean readOnlyHost) {
        return getSecondaryConfiguration("secondary", hostConfigPath, testName, baseConfig, readOnlyHost);
    }

    public static WildFlyManagedConfiguration getSecondaryConfiguration(String hostName, String hostConfigPath, String testName,
                                                                    WildFlyManagedConfiguration baseConfig,
                                                                    boolean readOnlyHost) {
        return getSecondaryConfiguration(hostName, hostConfigPath, testName, baseConfig, readOnlyHost, Boolean.getBoolean("wildfly.secondary.debug"));
    }

    public static WildFlyManagedConfiguration getSecondaryConfiguration(String hostName, String hostConfigPath, String testName,
                                                                    WildFlyManagedConfiguration baseConfig,
                                                                    boolean readOnlyHost, boolean debug) {
        return Configuration.getSecondaryConfiguration(hostConfigPath, testName, hostName, baseConfig, readOnlyHost, debug);
    }

    private static URI toURI(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static void startHosts(long timeout, DomainLifecycleUtil... hosts) {
        Future<?>[] futures = new Future<?>[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            futures[i] = hosts[i].startAsync();
        }

        processFutures(futures, timeout);
    }

    public static void stopHosts(long timeout, DomainLifecycleUtil... hosts) {
        Future<?>[] futures = new Future<?>[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            futures[i] = hosts[i].stopAsync();
        }

        processFutures(futures, timeout);
    }

    /**
     * Gets the base dir in which content specific to {@code testname} should be written.
     *
     * @param testName name identifying a test or test suite whose written output so be segregated from other output
     * @return the file representing the base dir
     */
    @SuppressWarnings("WeakerAccess")
    public static File getBaseDir(String testName) {
        return new File("target" + File.separator + "domains" + File.separator + testName);
    }

    /**
     * Gets the dir under {@link #getBaseDir(String)} in which content specific to a particular host should be written.
     *
     * @param testName name identifying a test or test suite whose written output so be segregated from other output
     * @param hostName the name of the host
     * @return the file representing the host's dir
     */
    public static File getHostDir(String testName, String hostName) {
        return new File(getBaseDir(testName), hostName);
    }

    /**
     * Gets the dir under {@link #getBaseDir(String)} in which additional JBoss Modules modules used by
     * the test should be written.
     *
     * @param testName name identifying a test or test suite whose written output so be segregated from other output
     * @return the file representing the host's dir
     */
    public static File getAddedModulesDir(String testName) {
        File f = new File(getBaseDir(testName), "added-modules");
        checkedMkDirs(f);
        return f;
    }

    /**
     * Gets the dir under {@link #getHostDir(String, String)} )} in which additional JBoss Modules modules used by
     * the test but targetted to a particular host should be written.
     *
     * @param testName name identifying a test or test suite whose written output so be segregated from other output
     * @return the file representing the host's dir
     */
    @SuppressWarnings("WeakerAccess")
    public static File getHostOverrideModulesDir(String testName, String hostName) {
        final File f = new File(getHostDir(testName, hostName), "added-modules");
        checkedMkDirs(f);
        return f;
    }

    public static ModelNode createOperationNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        ModelNode list = op.get("address").setEmptyList();
        if (address != null) {
            String [] pathSegments = address.split("/");
            for (String segment : pathSegments) {
                String[] elements = segment.split("=");
                list.add(elements[0], elements[1]);
            }
        }
        op.get("operation").set(operation);
        return op;
    }

    public static ModelNode validateResponse(ModelNode response) {
        return validateResponse(response, true);
    }

    public static ModelNode validateResponse(ModelNode response, boolean getResult) {

        if(! SUCCESS.equals(response.get(OUTCOME).asString())) {
            System.out.println("Failed response:");
            System.out.println(response);
            Assert.fail(response.get(FAILURE_DESCRIPTION).toString());
        }

        if (getResult) {
            Assert.assertTrue("result exists", response.has(RESULT));
            return response.get(RESULT);
        }
        return null;
    }

    public static ModelNode validateFailedResponse(ModelNode response) {

        if(! FAILED.equals(response.get(OUTCOME).asString())) {
            System.out.println("Response succeeded:");
            System.out.println(response);
            Assert.fail(response.get(OUTCOME).toString());
        }

        Assert.assertTrue("failure description exists", response.has(FAILURE_DESCRIPTION));
        return response.get(FAILURE_DESCRIPTION);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static void cleanFile(File file) {
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        cleanFile(child);
                    }
                }
            }
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    public static void safeClose(final AutoCloseable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Throwable t) {
            log.errorf(t, "Failed to close resource %s", closeable);
        }
    }

    private static void processFutures(Future<?>[] futures, long timeout) {

        try {
            for (int i = 0; i < futures.length; i++) {
                try {
                    futures[i].get(timeout, TimeUnit.MILLISECONDS);
                }  catch (ExecutionException e){
                    throw e.getCause();
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw  new RuntimeException(e);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void configureModulePath(WildFlyManagedConfiguration config, File... extraModules) {
        String basePath = config.getModulePath();
        if (basePath == null || basePath.isEmpty()) {
            basePath = config.getJbossHome() + File.separatorChar + "modules";
        }
        final StringBuilder path = new StringBuilder();
        for(final File extraModule : extraModules) {
            path.append(extraModule.getAbsolutePath()).append(File.pathSeparatorChar);
        }
        path.append(basePath);
        config.setModulePath(path.toString());
    }

    private static void checkedMkDirs(File f) {
        if (!f.mkdirs() && !f.exists()) {
            throw new RuntimeException("Cannot create dir " + f);
        }
    }

    private final WildFlyManagedConfiguration primaryConfiguration;
    private final WildFlyManagedConfiguration secondaryConfiguration;
    private final DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private final DomainLifecycleUtil domainSecondaryLifecycleUtil;
    private final DomainControllerClientConfig sharedClientConfig;
    private final String testClass;
    private volatile boolean closed;



    protected DomainTestSupport(final String testClass, final String domainConfig, final String primaryConfig,
                                final String secondaryConfig, WildFlyManagedConfiguration primaryBase,
                                final WildFlyManagedConfiguration secondaryBase) throws Exception {
        this(testClass, domainConfig, primaryConfig, secondaryConfig, primaryBase, secondaryBase, false, false, false);
    }

    protected DomainTestSupport(final String testClass, final String domainConfig, final String primaryConfig,
                                final String secondaryConfig, WildFlyManagedConfiguration primaryBase,
                                final WildFlyManagedConfiguration secondaryBase, final boolean readOnlyDomainConfig,
                                final boolean readOnlyPrimaryHostConfig, final boolean readOnlySecondaryHostConfig) throws Exception {
        this(testClass, getPrimaryConfiguration(domainConfig, primaryConfig, testClass, primaryBase, readOnlyDomainConfig, readOnlyPrimaryHostConfig),
                secondaryConfig == null ? null : getSecondaryConfiguration(secondaryConfig, testClass, secondaryBase, readOnlySecondaryHostConfig));
    }

    protected DomainTestSupport(final String testClass, final WildFlyManagedConfiguration primaryConfiguration,
                                final WildFlyManagedConfiguration secondaryConfiguration) throws Exception {
        this.testClass = testClass;
        this.sharedClientConfig = DomainControllerClientConfig.create();
        this.primaryConfiguration = primaryConfiguration;
        this.domainPrimaryLifecycleUtil = new DomainLifecycleUtil(primaryConfiguration, sharedClientConfig);
        this.secondaryConfiguration = secondaryConfiguration;
        if (secondaryConfiguration != null) {
            this.domainSecondaryLifecycleUtil = new DomainLifecycleUtil(secondaryConfiguration, sharedClientConfig);
        } else {
            this.domainSecondaryLifecycleUtil = null;
        }
    }

    public static DomainTestSupport create(final Configuration configuration) throws Exception {
        return new DomainTestSupport(configuration.getTestName(), configuration.getPrimaryConfiguration(), configuration.getSecondaryConfiguration());
    }

    public static DomainTestSupport create(final String testClass, final Configuration configuration) throws Exception {
        return new DomainTestSupport(testClass, configuration.getPrimaryConfiguration(), configuration.getSecondaryConfiguration());
    }

    public static DomainTestSupport create(final String testClass, final WildFlyManagedConfiguration primaryConfiguration,
                                           final WildFlyManagedConfiguration secondaryConfiguration) throws Exception {
        return new DomainTestSupport(testClass, primaryConfiguration, secondaryConfiguration);
    }

    public WildFlyManagedConfiguration getDomainPrimaryConfiguration() {
        return primaryConfiguration;
    }

    /**
     * Gets the {@link DomainLifecycleUtil} object for interacting with the primary @{code HostController}.
     * @return the util object. Will not return {@code null}
     *
     * @throws IllegalStateException if {@link #close()} has previously been called
     */
    public DomainLifecycleUtil getDomainPrimaryLifecycleUtil() {
        checkClosed();
        assert(domainPrimaryLifecycleUtil != null);
        return domainPrimaryLifecycleUtil;
    }

    public WildFlyManagedConfiguration getDomainSecondaryConfiguration() {
        return secondaryConfiguration;
    }

    /**
     * Gets the {@link DomainLifecycleUtil} object for interacting with the non-primary @{code HostController}, if there
     * is one.
     * @return the util object. May return {@code null} if this object was not configured to provide a non-primary.
     *
     * @throws IllegalStateException if {@link #close()} has previously been called
     */
    public DomainLifecycleUtil getDomainSecondaryLifecycleUtil() {
        checkClosed();
        return domainSecondaryLifecycleUtil;
    }

    public DomainControllerClientConfig getSharedClientConfiguration() {
        return sharedClientConfig;
    }

    /**
     * Starts the {@link DomainLifecycleUtil} objects managed by this object.
     *
     * @throws IllegalStateException if {@link #close()} has previously been called
     */
    public void start() {
        checkClosed();
        domainPrimaryLifecycleUtil.start();
        if (domainSecondaryLifecycleUtil != null) {
            try {
                domainSecondaryLifecycleUtil.start();
            } catch (RuntimeException e) {
                try {
                    //Clean up after ourselves if secondary failed to start
                    domainPrimaryLifecycleUtil.stop();
                } catch (RuntimeException ignore) {
                }
                throw e;
            }
        }
    }

    /**
     * Adds a new module to the {@link #getAddedModulesDir(String) added module dir} associated with the
     * test suite or class for which this object is providing support.
     *
     * @param moduleName the name of the module
     * @param moduleXml  stream providing the contents of the module's {@code moudle.xml} file
     * @param contents   map of module contents, keyed by the name of the content
     *
     * @throws IllegalStateException if {@link #close()} has previously been called
     */
    public void addTestModule(String moduleName, InputStream moduleXml, Map<String, StreamExporter> contents) throws IOException {
        checkClosed();
        File modulesDir = getAddedModulesDir(testClass);
        addModule(modulesDir, moduleName, moduleXml, contents);
    }

    /**
     * Adds a new module to the {@link #getHostOverrideModulesDir(String, String) host overrides module dir} associated with the
     * test suite or class for which this object is providing support.
     *
     * @param moduleName the name of the module
     * @param moduleXml  stream providing the contents of the module's {@code moudle.xml} file
     * @param contents   map of module contents, keyed by the name of the content
     *
     * @throws IllegalStateException if {@link #close()} has previously been called
     */
    public void addOverrideModule(String hostName, String moduleName, InputStream moduleXml, Map<String, StreamExporter> contents) throws IOException {
        checkClosed();
        File modulesDir = getHostOverrideModulesDir(testClass, hostName);
        addModule(modulesDir, moduleName, moduleXml, contents);
    }

    private static void addModule(final File modulesDir, String moduleName, InputStream moduleXml, Map<String, StreamExporter> resources) throws IOException {
        String modulePath = moduleName.replace('.', File.separatorChar) + File.separatorChar + "main";
        File moduleDir = new File(modulesDir, modulePath);
        checkedMkDirs(moduleDir);
        FileUtils.copyFile(moduleXml, new File(moduleDir, "module.xml"));
        for (Map.Entry<String, StreamExporter> entry : resources.entrySet()) {
            entry.getValue().exportTo(new File(moduleDir, entry.getKey()), true);
        }
    }

    /**
     * Stops the {@link #getDomainPrimaryLifecycleUtil() primary host} and, if there is one, the
     * {@link #getDomainSecondaryLifecycleUtil() secondary host}, and also closes any
     * {@link #getSharedClientConfiguration() shared client configuration}. This object and any
     * {@link DomainLifecycleUtil} objects obtained from it cannot be used
     * for controlling or interacting with hosts after this is called.
     */
    public void close() {
        closed = true;
        try {
            try {
                if (domainSecondaryLifecycleUtil != null) {
                    domainSecondaryLifecycleUtil.close();
                }
            } finally {
                domainPrimaryLifecycleUtil.close();
            }
        } finally {
            StreamUtils.safeClose(sharedClientConfig);
        }
    }

    /**
     * Calls {@link #close()}. This object cannot be used for controlling or interacting with hosts after
     * this is called.
     *
     * @deprecated Use {@link #close()}
     */
    @Deprecated
    public void stop() {
        close();
    }

    /**
     * Stops the {@link #getDomainPrimaryLifecycleUtil() primary host} and, if there is one, the
     * {@link #getDomainSecondaryLifecycleUtil() secondary host}. Stops are done concurrently with 2 minute
     * timeout for a host to stop.
     */
    public void stopHosts() {
        //checkClosed(); -- don't fail if already closed, as this is harmless
        if (domainSecondaryLifecycleUtil != null) {
            stopHosts(120000, domainSecondaryLifecycleUtil, domainPrimaryLifecycleUtil);
        } else {
            stopHosts(120000, domainPrimaryLifecycleUtil);
        }
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException(getClass().getSimpleName() + " is closed");
        }
    }

    public static class Configuration {

        private final String testName;
        private final WildFlyManagedConfiguration primaryConfiguration;
        private final WildFlyManagedConfiguration secondaryConfiguration;


        protected Configuration(final String testName, WildFlyManagedConfiguration primaryConfiguration,
                                WildFlyManagedConfiguration secondaryConfiguration) {
            this.testName = testName;
            this.primaryConfiguration = primaryConfiguration;
            this.secondaryConfiguration = secondaryConfiguration;
        }

        public String getTestName() {
            return testName;
        }

        public WildFlyManagedConfiguration getPrimaryConfiguration() {
            return primaryConfiguration;
        }

        public WildFlyManagedConfiguration getSecondaryConfiguration() {
            return secondaryConfiguration;
        }

        public static Configuration create(final String testName, final String domainConfig, final String primaryConfig, final String secondaryConfig) {
            return create(testName, domainConfig, primaryConfig, secondaryConfig, false, false, false);
        }

        @SuppressWarnings("WeakerAccess")
        public static Configuration createDebugPrimary(final String testName, final String domainConfig, final String primaryConfig, final String secondaryConfig) {
            return create(testName, domainConfig, primaryConfig, secondaryConfig, false, false, true, false, false);
        }

        @SuppressWarnings("WeakerAccess")
        public static Configuration createDebugSecondary(final String testName, final String domainConfig, final String primaryConfig, final String secondaryConfig) {
            return create(testName, domainConfig, primaryConfig, secondaryConfig, false, false, false, false, true);
        }

        public static Configuration create(final String testName, final String domainConfig, final String primaryConfig,
                                           final String secondaryConfig, boolean readOnlyPrimaryDomain, boolean readOnlyPrimaryHost, boolean readOnlySecondaryHost) {
            return create(testName, domainConfig, primaryConfig, secondaryConfig, readOnlyPrimaryDomain, readOnlyPrimaryHost, false, readOnlySecondaryHost, false);
        }

        public static Configuration create(final String testName, final String domainConfig, final String primaryConfig,
                                           final String secondaryConfig,
                                           boolean readOnlyPrimaryDomain, boolean readOnlyPrimaryHost, boolean primaryDebug,
                                           boolean readOnlySecondaryHost, boolean secondaryDebug) {

            WildFlyManagedConfiguration primaryConfiguration = getPrimaryConfiguration(domainConfig, primaryConfig, testName, null, readOnlyPrimaryDomain, readOnlyPrimaryHost, primaryDebug);
            WildFlyManagedConfiguration secondaryConfiguration = secondaryConfig == null ? null : getSecondaryConfiguration(secondaryConfig, testName, "secondary", null, readOnlySecondaryHost, secondaryDebug);
            return new Configuration(testName, primaryConfiguration, secondaryConfiguration);
        }

        private static WildFlyManagedConfiguration getPrimaryConfiguration(String domainConfigPath, String hostConfigPath,
                                                                         String testName, WildFlyManagedConfiguration baseConfig,
                                                                         boolean readOnlyDomain, boolean readOnlyHost, boolean debug) {
            final String hostName = "primary";
            File domains = getBaseDir(testName);
            File extraModules = getAddedModulesDir(testName);
            File overrideModules = getHostOverrideModulesDir(testName, hostName);
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            final WildFlyManagedConfiguration primaryConfig = baseConfig == null ? new WildFlyManagedConfiguration() : baseConfig;
            configureModulePath(primaryConfig, overrideModules, extraModules);
            primaryConfig.setHostControllerManagementAddress(primaryAddress);
            primaryConfig.setHostCommandLineProperties("-Djboss.test.host.primary.address=" + primaryAddress);
            if(debug) {
                primaryConfig.setHostCommandLineProperties("-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y " +
                       primaryConfig.getHostCommandLineProperties());
            }
            primaryConfig.setReadOnlyDomain(readOnlyDomain);
            primaryConfig.setReadOnlyHost(readOnlyHost);
            URL url = tccl.getResource(domainConfigPath);
            assert url != null : "cannot find domainConfigPath";
            primaryConfig.setDomainConfigFile(new File(toURI(url)).getAbsolutePath());
            url = tccl.getResource(hostConfigPath);
            assert url != null : "cannot find hostConfigPath";
            primaryConfig.setHostConfigFile(new File(toURI(url)).getAbsolutePath());
            File primaryDir = new File(domains, hostName);
            // TODO this should not be necessary
            File cfgDir = new File(primaryDir, "configuration");
            checkedMkDirs(cfgDir);
            primaryConfig.setDomainDirectory(primaryDir.getAbsolutePath());
            if (primaryJvmHome != null) primaryConfig.setJavaHome(primaryJvmHome);
            if (primaryControllerJvmHome != null) primaryConfig.setControllerJavaHome(primaryControllerJvmHome);
            return primaryConfig;
        }

        private static WildFlyManagedConfiguration getSecondaryConfiguration(String hostConfigPath, String testName,
                                                                         String hostName, WildFlyManagedConfiguration baseConfig,
                                                                         boolean readOnlyHost, boolean debug) {
            File domains = getBaseDir(testName);
            File extraModules = getAddedModulesDir(testName);
            File overrideModules = getHostOverrideModulesDir(testName, hostName);
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            final WildFlyManagedConfiguration secondaryConfig = baseConfig == null ? new WildFlyManagedConfiguration() : baseConfig;
            configureModulePath(secondaryConfig, overrideModules, extraModules);
            secondaryConfig.setHostName(hostName);
            secondaryConfig.setHostControllerManagementAddress(secondaryAddress);
            secondaryConfig.setHostControllerManagementPort(19999);
            secondaryConfig.setHostCommandLineProperties("-Djboss.test.host.primary.address=" + primaryAddress +
                    " -Djboss.test.host.secondary.address=" + secondaryAddress);
            if(debug) {
                secondaryConfig.setHostCommandLineProperties("-agentlib:jdwp=transport=dt_socket,address=8788,server=y,suspend=y " +
                       secondaryConfig.getHostCommandLineProperties());
            }
            secondaryConfig.setReadOnlyHost(readOnlyHost);
            URL url = tccl.getResource(hostConfigPath);
            assert url != null;
            secondaryConfig.setHostConfigFile(new File(toURI(url)).getAbsolutePath());
            File secondaryDir = new File(domains, hostName);
            // TODO this should not be necessary
            File cfgDir = new File(secondaryDir, "configuration");
            checkedMkDirs(cfgDir);
            secondaryConfig.setDomainDirectory(secondaryDir.getAbsolutePath());
            if (secondaryJvmHome != null) secondaryConfig.setJavaHome(secondaryJvmHome);
            if (secondaryControllerJvmHome != null) secondaryConfig.setControllerJavaHome(secondaryControllerJvmHome);
            return secondaryConfig;
        }
    }


}
