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

package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Configuration persister that can delegate to a domain or host persister depending what needs to be persisted.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HostControllerConfigurationPersister implements ExtensibleConfigurationPersister {

    private final HostControllerEnvironment environment;
    private ExtensibleConfigurationPersister domainPersister;
    private final ExtensibleConfigurationPersister hostPersister;
    private final LocalHostControllerInfo hostControllerInfo;
    private final ExecutorService executorService;
    private final ExtensionRegistry hostExtensionRegistry;
    private final ExtensionRegistry extensionRegistry;
    private Boolean slave;

    public HostControllerConfigurationPersister(final HostControllerEnvironment environment, final LocalHostControllerInfo localHostControllerInfo,
                                                final ExecutorService executorService, final ExtensionRegistry hostExtensionRegistry, final ExtensionRegistry extensionRegistry) {
        this.environment = environment;
        this.hostControllerInfo = localHostControllerInfo;
        this.executorService = executorService;
        this.hostExtensionRegistry = hostExtensionRegistry;
        this.extensionRegistry = extensionRegistry;
        final ConfigurationFile configurationFile = environment.getHostConfigurationFile();
        final HostRunningModeControl runningModeControl = environment.getRunningModeControl();
        if (runningModeControl.isReloaded()) {
            configurationFile.resetBootFile(runningModeControl.isUseCurrentConfig(), runningModeControl.getAndClearNewBootFileName());
        }
        this.hostPersister = ConfigurationPersisterFactory.createHostXmlConfigurationPersister(configurationFile, environment, executorService, hostExtensionRegistry);
    }

    public void initializeDomainConfigurationPersister(boolean slave) {
        if (domainPersister != null) {
            throw HostControllerLogger.ROOT_LOGGER.configurationPersisterAlreadyInitialized();
        }

        final File configDir = environment.getDomainConfigurationDir();
        ConfigurationFile domainConfigurationFile = null;
        if (slave) {
            // in either case of --backup and/or --cached-dc, we persist with the same persister
            if (environment.isBackupDomainFiles() || environment.isUseCachedDc()) {
                domainConfigurationFile = getBackupDomainConfigurationFile();
                domainPersister = ConfigurationPersisterFactory.createRemoteBackupDomainXmlConfigurationPersister(configDir, executorService, extensionRegistry);
            } else {
                domainPersister = ConfigurationPersisterFactory.createTransientDomainXmlConfigurationPersister(executorService, extensionRegistry);
            }
        } else {
            domainConfigurationFile = getStandardDomainConfigurationFile();
            final HostRunningModeControl runningModeControl = environment.getRunningModeControl();
            if (runningModeControl.isReloaded()) {
                if (environment.isBackupDomainFiles()) {
                    // We may have been promoted to master and reloaded.
                    // See if we should still use the domain-cached-remote.xml.
                    // If the standard file is newer or same age, we assume the user has either moved
                    // the cached-remote file to the standard file location, or has copied in the
                    // standard file from elsewhere. Prior to WFLY-3108 doing one of these was required,
                    // so we must support people who continue to do so. Plus it's valid to want to
                    // use the standard file name.
                    ConfigurationFile cachedRemote = getBackupDomainConfigurationFile();
                    File cachedRemoteFile = cachedRemote.getBootFile();
                    if (cachedRemoteFile.exists()
                            && cachedRemoteFile.lastModified() > domainConfigurationFile.getBootFile().lastModified()) {
                        domainConfigurationFile = cachedRemote;
                    }
                }
                domainConfigurationFile.resetBootFile(
                        runningModeControl.isUseCurrentDomainConfig(),
                        runningModeControl.getAndClearNewDomainBootFileName());
            }
            domainPersister = ConfigurationPersisterFactory.createDomainXmlConfigurationPersister(domainConfigurationFile, executorService, extensionRegistry, environment);
        }
        // Store this back to environment so mgmt api that exposes it can still work
        environment.setDomainConfigurationFile(domainConfigurationFile);

        this.slave = slave;
    }

    public boolean isSlave() {
        if (slave == null) {
            throw HostControllerLogger.ROOT_LOGGER.mustInvokeBeforeCheckingSlaveStatus("initializeDomainConfigurationPersister");
        }
        return slave;
    }

    public ExtensibleConfigurationPersister getDomainPersister() {
        if (domainPersister == null) {
            throw HostControllerLogger.ROOT_LOGGER.mustInvokeBeforePersisting("initializeDomainConfigurationPersister");
        }
        return domainPersister;
    }

    public ExtensibleConfigurationPersister getHostPersister() {
        return hostPersister;
    }

    @Override
    public PersistenceResource store(ModelNode model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
        final PersistenceResource[] delegates = new PersistenceResource[2];
        for (PathAddress addr : affectedAddresses) {
            if (delegates[0] == null && addr.size() > 0 && HOST.equals(addr.getElement(0).getKey()) && addr.getElement(0).getValue().equals(hostControllerInfo.getLocalHostName())) {
                ModelNode hostModel = new ModelNode();
                hostModel.set(model.get(HOST, hostControllerInfo.getLocalHostName()));
                delegates[0] = hostPersister.store(hostModel, affectedAddresses);
            } else if (delegates[1] == null && (addr.size() == 0 || !HOST.equals(addr.getElement(0).getKey()))) {
                delegates[1] = getDomainPersister().store(model, affectedAddresses);
            }

            if (delegates[0] != null && delegates[1] != null) {
                break;
            }
        }

        return new PersistenceResource() {
            @Override
            public void commit() {
                if (delegates[0] != null) {
                    delegates[0].commit();
                }
                if (delegates[1] != null) {
                    delegates[1].commit();
                }
            }

            @Override
            public void rollback() {
                if (delegates[0] != null) {
                    delegates[0].rollback();
                }
                if (delegates[1] != null) {
                    delegates[1].rollback();
                }
            }
        };
    }

    @Override
    public void marshallAsXml(ModelNode model, OutputStream output) throws ConfigurationPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ModelNode> load() throws ConfigurationPersistenceException {
        final ConfigurationFile configurationFile = environment.getHostConfigurationFile();
        final File bootFile = configurationFile.getBootFile();
        final ConfigurationFile.InteractionPolicy policy = configurationFile.getInteractionPolicy();
        final HostRunningModeControl runningModeControl = environment.getRunningModeControl();

        if (policy == ConfigurationFile.InteractionPolicy.NEW && (bootFile.exists() && bootFile.length() != 0)) {
            throw HostControllerLogger.ROOT_LOGGER.cannotOverwriteDomainXmlWithEmpty(bootFile.getName());
        }

        // if we started with new / discard but now we're reloading, ignore it. Otherwise on a reload, we have no way to drop the --empty-host-config
        if (!runningModeControl.isReloaded() && (policy == ConfigurationFile.InteractionPolicy.NEW || policy == ConfigurationFile.InteractionPolicy.DISCARD)) {
            return new ArrayList<>();
        }
        return hostPersister.load();
    }

    @Override
    public void successfulBoot() throws ConfigurationPersistenceException {
        final ConfigurationFile configurationFile = environment.getHostConfigurationFile();
        ConfigurationFile.InteractionPolicy policy = configurationFile.getInteractionPolicy();

        hostPersister.successfulBoot();
        if (domainPersister != null) {
            domainPersister.successfulBoot();
        }
    }

    @Override
    public String snapshot() throws ConfigurationPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SnapshotInfo listSnapshots() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteSnapshot(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerSubsystemWriter(String name, XMLElementWriter<SubsystemMarshallingContext> writer) {
        domainPersister.registerSubsystemWriter(name, writer);
    }

    @Override
    public void registerSubsystemWriter(String name, Supplier<XMLElementWriter<SubsystemMarshallingContext>> writer) {
        domainPersister.registerSubsystemWriter(name, writer);
    }

    @Override
    public void unregisterSubsystemWriter(String name) {
        domainPersister.unregisterSubsystemWriter(name);
    }

    private ConfigurationFile getStandardDomainConfigurationFile() {
        final String defaultDomainConfig = WildFlySecurityManager.getPropertyPrivileged(HostControllerEnvironment.JBOSS_DOMAIN_DEFAULT_CONFIG, "domain.xml");
        final String initialDomainConfig = environment.getInitialDomainConfig();
        return new ConfigurationFile(environment.getDomainConfigurationDir(), defaultDomainConfig,
                initialDomainConfig == null ? environment.getDomainConfig() : initialDomainConfig, environment.getDomainConfigurationFileInteactionPolicy());
    }

    private ConfigurationFile getBackupDomainConfigurationFile() {
        return new ConfigurationFile(environment.getDomainConfigurationDir(), ConfigurationPersisterFactory.CACHED_DOMAIN_XML,
                null, environment.getInitialDomainConfig() == null);
    }
}
