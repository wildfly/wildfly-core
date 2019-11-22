/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.operations;

import java.util.ArrayList;
import java.util.List;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.discovery.DiscoveryOption;
import org.jboss.as.host.controller.model.host.AdminOnlyDomainConfigPolicy;
import org.jboss.msc.service.ServiceName;

/**
 * Default implementation of {@link LocalHostControllerInfo}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class LocalHostControllerInfoImpl implements LocalHostControllerInfo {

    private final ControlledProcessState processState;
    private final HostControllerEnvironment hostEnvironment;

    private String localHostName;
    // defaulted to true for booting with empty configs.
    private volatile boolean master = true;
    private volatile String nativeManagementInterface;
    private volatile int nativeManagementPort;

    private volatile String remoteDCUser;
    private volatile String remoteSecurityRealm;
    private volatile ServiceName authenticationContext;
    private volatile List<DiscoveryOption> remoteDiscoveryOptions = new ArrayList<DiscoveryOption>();
    private volatile boolean remoteIgnoreUnaffectedConfiguration;
    private volatile String httpManagementInterface;
    private volatile int httpManagementPort;
    private volatile String httpManagementSecureInterface;
    private volatile int httpManagementSecurePort;
    private volatile AdminOnlyDomainConfigPolicy adminOnlyDomainConfigPolicy = AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG;
    private volatile boolean overrideLocalHostName = false;

    /** Constructor solely for test cases */
    public LocalHostControllerInfoImpl(final ControlledProcessState processState, final String localHostName) {
        this.processState = processState;
        this.hostEnvironment = null;
        this.localHostName = localHostName;
    }

    public LocalHostControllerInfoImpl(final ControlledProcessState processState, final HostControllerEnvironment hostEnvironment) {
        this.processState = processState;
        this.hostEnvironment = hostEnvironment;
        this.localHostName = hostEnvironment.getRunningModeControl().getReloadHostName();
        if (localHostName != null)
            overrideLocalHostName = true;
    }

    @Override
    public String getLocalHostName() {
        // this is to allow host persistence to work when booting with an empty host.xml
        if (overrideLocalHostName) {
            return localHostName;
        }
        return hostEnvironment == null ? localHostName : hostEnvironment.getHostControllerName();
    }

    void setLocalHostName(final String hostName) {
        this.overrideLocalHostName = true;
        this.localHostName = hostName;
    }

    public void clearOverrideLocalHostName() {
        this.overrideLocalHostName = false;
    }

    @Override
    public ControlledProcessState.State getProcessState() {
        return processState.getState();
    }

    @Override
    public boolean isMasterDomainController() {
        return master;
    }

    public ServiceName getAuthenticationContext() {
        return authenticationContext;
    }

    @Override
    public String getNativeManagementInterface() {
        return nativeManagementInterface;
    }

    @Override
    public int getNativeManagementPort() {
        return nativeManagementPort;
    }

    @Override
    public String getHttpManagementInterface() {
        return httpManagementInterface;
    }

    @Override
    public int getHttpManagementPort() {
        return httpManagementPort;
    }

    @Override
    public String getHttpManagementSecureInterface() {
        return httpManagementSecureInterface == null ? httpManagementInterface : httpManagementSecureInterface;
    }

    @Override
    public int getHttpManagementSecurePort() {
        return httpManagementSecurePort;
    }

    @Override
    public String getRemoteDomainControllerUsername() {
        return remoteDCUser;
    }

    /**
     * @deprecated Client side security configuration should be obtained from an AuthenticationContext.
     */
    @Deprecated
    public String getRemoteDomainControllerSecurityRealm() {
        return remoteSecurityRealm;
    }

    @Override
    public List<DiscoveryOption> getRemoteDomainControllerDiscoveryOptions() {
        return remoteDiscoveryOptions;
    }

    @Override
    public boolean isRemoteDomainControllerIgnoreUnaffectedConfiguration() {
        return remoteIgnoreUnaffectedConfiguration;
    }

    /**
     * This indicates the process was started with '--backup', or has ignore-unused-configuration=false in host.xml and intends
     * to maintain a backup of the entire domain configuration, including deployments.
     * @return
     */
    @Override
    public boolean isBackupDc() {
        return hostEnvironment == null ? false : hostEnvironment.isBackupDomainFiles();
    }

    @Override
    public boolean isUsingCachedDc() {
        return hostEnvironment == null ? false : hostEnvironment.isUseCachedDc();
    }

    public AdminOnlyDomainConfigPolicy getAdminOnlyDomainConfigPolicy() {
        return adminOnlyDomainConfigPolicy;
    }

    void setAdminOnlyDomainConfigPolicy(AdminOnlyDomainConfigPolicy adminOnlyDomainConfigPolicy) {
        this.adminOnlyDomainConfigPolicy = adminOnlyDomainConfigPolicy;
    }

    void setAuthenticationContext(ServiceName authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    void setMasterDomainController(boolean master) {
        this.master = master;
    }

    void setNativeManagementInterface(String nativeManagementInterface) {
        this.nativeManagementInterface = nativeManagementInterface;
    }

    void setNativeManagementPort(int nativeManagementPort) {
        this.nativeManagementPort = nativeManagementPort;
    }

    void setHttpManagementInterface(String httpManagementInterface) {
        this.httpManagementInterface = httpManagementInterface;
    }

    void setHttpManagementPort(int httpManagementPort) {
        this.httpManagementPort = httpManagementPort;
    }

    void setHttpManagementSecureInterface(String httpManagementSecureInterface) {
        this.httpManagementSecureInterface = httpManagementSecureInterface;
    }

    void setHttpManagementSecurePort(int httpManagementSecurePort) {
        this.httpManagementSecurePort = httpManagementSecurePort;
    }

    void setRemoteDomainControllerUsername(String userName) {
        this.remoteDCUser = userName;
    }

    void setRemoteDomainControllerSecurityRealm(String remoteSecurityRealm) {
        this.remoteSecurityRealm = remoteSecurityRealm;
    }

    void addRemoteDomainControllerDiscoveryOption(DiscoveryOption discoveryOption) {
        this.remoteDiscoveryOptions.add(discoveryOption);
    }

    void setRemoteDomainControllerIgnoreUnaffectedConfiguration(boolean ignore) {
        this.remoteIgnoreUnaffectedConfiguration = ignore;
    }
}
