/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management.util;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.as.version.Stability;

/**
 * WildFlyManagedConfiguration
 *
 * @author Brian Stansberry
 */
public class WildFlyManagedConfiguration {

    public static WildFlyManagedConfiguration createFromClassLoaderResources(String domainConfigPath,
                                                                             String hostConfigPath) {
        WildFlyManagedConfiguration result = new WildFlyManagedConfiguration();
        if (domainConfigPath != null) {
            result.setDomainConfigFile(loadConfigFileFromContextClassLoader(domainConfigPath));
        }
        if (hostConfigPath != null) {
            result.setHostConfigFile(hostConfigPath);
        }
        return result;
    }

    public static String loadConfigFileFromContextClassLoader(String resourcePath) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        URL url = tccl.getResource(resourcePath);
        assert url != null : "cannot find resource at path " + resourcePath;
        return new File(toURI(url)).getAbsolutePath();
    }

    private static URI toURI(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String jbossHome = System.getProperty("jboss.home");

    private String javaHome = System.getenv("JAVA_HOME");

    private String controllerJavaHome = System.getenv("JAVA_HOME");

    private String modulePath = System.getProperty("module.path");

    private String javaVmArguments = System.getProperty("server.jvm.args", "-Xmx512m");

    private int startupTimeoutInSeconds = TimeoutUtil.adjust(120);

    private boolean outputToConsole = true;

    private String hostControllerManagementProtocol = "remote";

    private String hostControllerManagementAddress = System.getProperty("jboss.test.host.primary.address", "localhost");

    private int hostControllerManagementPort = 9999;

    private String hostName = "primary";

    private String domainDir;

    private String domainConfigFile;

    private String hostConfigFile;

    private String hostCommandLineProperties;

    private boolean adminOnly;

    private boolean readOnlyDomain;

    private boolean readOnlyHost;

    private CallbackHandler callbackHandler = Authentication.getCallbackHandler();

    private String mgmtUsersFile;

    private String mgmtGroupsFile;

    private boolean backupDC;

    private boolean cachedDC;

    private boolean enableAssertions = true;

    private boolean rewriteConfigFiles = true;

    private Stability stability;

    public WildFlyManagedConfiguration(String jbossHome) {
        if (jbossHome != null) {
            this.jbossHome = jbossHome;
            this.modulePath = new File(jbossHome, "modules").getAbsolutePath();
        }
    }

    public WildFlyManagedConfiguration() {
    }

    public void validate(){
        /*Validate.configurationDirectoryExists(jbossHome, "jbossHome must exist at " + jbossHome);
        if (javaHome != null) {
            Validate.configurationDirectoryExists(javaHome, "javaHome must exist at " + javaHome);
        }
        if (controllerJavaHome != null) {
            Validate.configurationDirectoryExists(controllerJavaHome, "controllerJavaHome must exist at " + controllerJavaHome);
        }*/
    }

    /**
     * @return the jbossHome
     */
    public String getJbossHome() {
        return jbossHome;
    }

    /**
     * @param jbossHome the jbossHome to set
     */
    public WildFlyManagedConfiguration setJbossHome(String jbossHome) {
        this.jbossHome = jbossHome;
        return this;
    }

    /**
     * @return the javaHome
     */
    public String getJavaHome() {
        return javaHome;
    }

    /**
     * @param javaHome the javaHome to set
     */
    public WildFlyManagedConfiguration setJavaHome(String javaHome) {
        this.javaHome = javaHome;
        return this;
    }

    /**
     * @return the controllerJavaHome
     */
    public String getControllerJavaHome() {
        return controllerJavaHome;
    }

    /**
     * @param controllerJavaHome the javaHome to set
     */
    public WildFlyManagedConfiguration setControllerJavaHome(String controllerJavaHome) {
        this.controllerJavaHome = controllerJavaHome;
        return this;
    }

    /**
     * @return the javaVmArguments
     */
    public String getJavaVmArguments() {
        return javaVmArguments;
    }

    /**
     * @param javaVmArguments the javaVmArguments to set
     */
    public WildFlyManagedConfiguration setJavaVmArguments(String javaVmArguments) {
        this.javaVmArguments = javaVmArguments;
        return this;
    }

    /**
     * @param startupTimeoutInSeconds the startupTimeoutInSeconds to set
     */
    public WildFlyManagedConfiguration setStartupTimeoutInSeconds(int startupTimeoutInSeconds) {
        this.startupTimeoutInSeconds = startupTimeoutInSeconds;
        return this;
    }

    /**
     * @return the startupTimeoutInSeconds
     */
    public int getStartupTimeoutInSeconds() {
        return startupTimeoutInSeconds;
    }

    /**
     * @param outputToConsole the outputToConsole to set
     */
    public WildFlyManagedConfiguration setOutputToConsole(boolean outputToConsole) {
        this.outputToConsole = outputToConsole;
        return this;
    }

    /**
     * @return the outputToConsole
     */
    public boolean isOutputToConsole() {
        return outputToConsole;
    }

    public String getHostControllerManagementProtocol() {
        return hostControllerManagementProtocol;
    }

    public WildFlyManagedConfiguration setHostControllerManagementProtocol(String hostControllerManagementProtocol) {
        this.hostControllerManagementProtocol = hostControllerManagementProtocol;
        return this;
    }

    public String getHostControllerManagementAddress() {
        return hostControllerManagementAddress;
    }

    public WildFlyManagedConfiguration setHostControllerManagementAddress(String hostControllerManagementAddress) {
        this.hostControllerManagementAddress = hostControllerManagementAddress;
        return this;
    }

    public int getHostControllerManagementPort() {
        return hostControllerManagementPort;
    }

    public WildFlyManagedConfiguration setHostControllerManagementPort(int hostControllerManagementPort) {
        this.hostControllerManagementPort = hostControllerManagementPort;
        return this;
    }

    public String getDomainDirectory() {
        return domainDir;
    }

    public WildFlyManagedConfiguration setDomainDirectory(String domainDir) {
        this.domainDir = domainDir;
        return this;
    }

    public String getDomainConfigFile() {
        return domainConfigFile;
    }

    public WildFlyManagedConfiguration setDomainConfigFile(String domainConfigFile) {
        this.domainConfigFile = domainConfigFile;
        return this;
    }

    public String getHostConfigFile() {
        return hostConfigFile;
    }

    public WildFlyManagedConfiguration setHostConfigFile(String hostConfigFile) {
        this.hostConfigFile = hostConfigFile;
        return this;
    }

    public String getMgmtUsersFile() {
        return mgmtUsersFile;
    }

    public WildFlyManagedConfiguration setMgmtUsersFile(String mgmtUsersFile) {
        this.mgmtUsersFile = mgmtUsersFile;
        return this;
    }

    public String getMgmtGroupsFile() {
        return mgmtGroupsFile;
    }

    public WildFlyManagedConfiguration setMgmtGroupsFile(String mgmtGroupsFile) {
        this.mgmtGroupsFile = mgmtGroupsFile;
        return this;
    }

    public String getHostCommandLineProperties() {
        return hostCommandLineProperties;
    }

    public WildFlyManagedConfiguration setHostCommandLineProperties(String hostCommandLineProperties) {
        this.hostCommandLineProperties = hostCommandLineProperties;
        return this;
    }

    public void addHostCommandLineProperty(String hostCommandLineProperty) {
        this.hostCommandLineProperties = this.hostCommandLineProperties == null
                ? hostCommandLineProperty : this.hostCommandLineProperties + " " + hostCommandLineProperty;
    }

    public String getHostName() {
        return hostName;
    }

    public WildFlyManagedConfiguration setHostName(String hostName) {
        this.hostName = hostName;
        return this;
    }

    public String getModulePath() {
        return modulePath;
    }

    public WildFlyManagedConfiguration setModulePath(final String modulePath) {
        this.modulePath = modulePath;
        return this;
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public WildFlyManagedConfiguration setAdminOnly(boolean adminOnly) {
        this.adminOnly = adminOnly;
        return this;
    }

    public boolean isReadOnlyDomain() {
        return readOnlyDomain;
    }

    public WildFlyManagedConfiguration setReadOnlyDomain(boolean readOnlyDomain) {
        this.readOnlyDomain = readOnlyDomain;
        return this;
    }

    public boolean isReadOnlyHost() {
        return readOnlyHost;
    }

    public WildFlyManagedConfiguration setReadOnlyHost(boolean readOnlyHost) {
        this.readOnlyHost = readOnlyHost;
        return this;
    }

    public CallbackHandler getCallbackHandler() {
        return callbackHandler;
    }

    public WildFlyManagedConfiguration setCallbackHandler(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
        return this;
    }

    public boolean isBackupDC() {
        return backupDC;
    }

    public WildFlyManagedConfiguration setBackupDC(boolean backupDC) {
        this.backupDC = backupDC;
        return this;
    }

    public boolean isCachedDC() {
        return cachedDC;
    }

    public WildFlyManagedConfiguration setCachedDC(boolean cachedDC) {
        this.cachedDC = cachedDC;
        return this;
    }

    public boolean isEnableAssertions() {
        return enableAssertions;
    }

    public WildFlyManagedConfiguration setEnableAssertions(boolean enableAssertions) {
        this.enableAssertions = enableAssertions;
        return this;
    }

    public boolean isRewriteConfigFiles() {
        return rewriteConfigFiles;
    }

    public WildFlyManagedConfiguration setRewriteConfigFiles(boolean rewriteConfigFiles) {
        this.rewriteConfigFiles = rewriteConfigFiles;
        return this;
    }

    public Stability getStability() {
        return this.stability;
    }

    public WildFlyManagedConfiguration setStability(Stability stability) {
        this.stability = stability;
        return this;
    }
}
