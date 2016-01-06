/*
 * Copyright (C) 2016 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.host.controller.operations.RemoteDomainControllerAddHandler.ADMIN_ONLY_POLICY;
import static org.jboss.as.host.controller.operations.RemoteDomainControllerAddHandler.IGNORE_UNUSED_CONFIG;
import static org.jboss.as.host.controller.operations.RemoteDomainControllerAddHandler.USERNAME;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.host.controller.HostControllerConfigurationPersister;
import org.jboss.as.host.controller.discovery.StaticDiscovery;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.model.host.AdminOnlyDomainConfigPolicy;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public abstract class DomainControllerWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    public static DomainControllerWriteAttributeHandler getInstance(final ManagementResourceRegistration rootRegistration,
                final LocalHostControllerInfoImpl hostControllerInfo,
                final HostControllerConfigurationPersister overallConfigPersister,
                final HostFileRepository localFileRepository,
                final HostFileRepository remoteFileRepository,
                final ContentRepository contentRepository,
                final DomainController domainController,
                final ExtensionRegistry extensionRegistry,
                final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                final PathManagerService pathManager) {
        return new RealLocalDomainControllerAddHandler(rootRegistration, hostControllerInfo, overallConfigPersister,
                localFileRepository, remoteFileRepository, contentRepository, domainController, extensionRegistry,
                ignoredDomainResourceRegistry, pathManager);
    }

    public static DomainControllerWriteAttributeHandler getTestInstance() {
        return new TestLocalDomainControllerAddHandler();
    }

    private DomainControllerWriteAttributeHandler() {
        super(org.jboss.as.host.controller.model.host.HostResourceDefinition.DOMAIN_CONTROLLER);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return !context.isBooting();
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        super.execute(context, operation);
        final ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
        ModelNode dc = model.get(DOMAIN_CONTROLLER);
        if (operation.hasDefined(VALUE, LOCAL)) {
            dc.get(LOCAL).setEmptyObject();
            if (dc.has(REMOTE)) {
                dc.remove(REMOTE);
            }
            if (context.isBooting()) {
                initializeLocalDomain();
            }
        } else if (operation.hasDefined(VALUE, REMOTE)) {
            if (dc.has(LOCAL)) {
                dc.remove(LOCAL);
            }
            if (context.isBooting()) {
                initializeRemoteDomain(context, model);
            }
        }
    }

    protected abstract void initializeLocalDomain();

    protected abstract void initializeRemoteDomain(OperationContext context, ModelNode remoteDC) throws OperationFailedException;

    private static class RealLocalDomainControllerAddHandler extends DomainControllerWriteAttributeHandler {

        private final ManagementResourceRegistration rootRegistration;
        private final HostControllerConfigurationPersister overallConfigPersister;
        private final LocalHostControllerInfoImpl hostControllerInfo;
        private final ContentRepository contentRepository;
        private final DomainController domainController;
        private final ExtensionRegistry extensionRegistry;
        private final PathManagerService pathManager;
        private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
        private final HostFileRepository localFileRepository;
        private final HostFileRepository remoteFileRepository;

        private RealLocalDomainControllerAddHandler(final ManagementResourceRegistration rootRegistration,
                final LocalHostControllerInfoImpl hostControllerInfo,
                final HostControllerConfigurationPersister overallConfigPersister,
                final HostFileRepository localFileRepository,
                final HostFileRepository remoteFileRepository,
                final ContentRepository contentRepository,
                final DomainController domainController,
                final ExtensionRegistry extensionRegistry,
                final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                final PathManagerService pathManager) {
            this.rootRegistration = rootRegistration;
            this.overallConfigPersister = overallConfigPersister;
            this.localFileRepository = localFileRepository;
            this.remoteFileRepository = remoteFileRepository;
            this.hostControllerInfo = hostControllerInfo;
            this.contentRepository = contentRepository;
            this.domainController = domainController;
            this.extensionRegistry = extensionRegistry;
            this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
            this.pathManager = pathManager;
        }

        @Override
        protected void initializeLocalDomain() {
            hostControllerInfo.setMasterDomainController(true);
            overallConfigPersister.initializeDomainConfigurationPersister(false);
            domainController.initializeMasterDomainRegistry(rootRegistration, overallConfigPersister.getDomainPersister(),
                    contentRepository, localFileRepository, extensionRegistry, pathManager);
        }

        @Override
        protected void initializeRemoteDomain(OperationContext context, ModelNode remoteDC) throws OperationFailedException {
            hostControllerInfo.setMasterDomainController(false);
            ModelNode protocolNode = RemoteDomainControllerAddHandler.PROTOCOL.resolveModelAttribute(context, remoteDC);
            ModelNode hostNode = RemoteDomainControllerAddHandler.HOST.resolveModelAttribute(context, remoteDC);
            ModelNode portNode = RemoteDomainControllerAddHandler.PORT.resolveModelAttribute(context, remoteDC);
            if (hostNode.isDefined() && portNode.isDefined()) {
                String host = hostNode.asString();
                int port = portNode.asInt();
                String protocol = protocolNode.asString();
                StaticDiscovery staticDiscoveryOption = new StaticDiscovery(protocol, host, port);
                hostControllerInfo.addRemoteDomainControllerDiscoveryOption(staticDiscoveryOption);
            }
            ModelNode usernameNode = USERNAME.resolveModelAttribute(context, remoteDC);
            if (usernameNode.isDefined()) {
                hostControllerInfo.setRemoteDomainControllerUsername(usernameNode.asString());
            }

            ModelNode ignoreUnusedConfiguration = IGNORE_UNUSED_CONFIG.resolveModelAttribute(context, remoteDC);

            if (!ignoreUnusedConfiguration.isDefined()) {
                if (hostControllerInfo.isBackupDc()) { // started up with --backup, ignore-unused-configuration not set
                    hostControllerInfo.setRemoteDomainControllerIgnoreUnaffectedConfiguration(false);
                } else {
                    hostControllerInfo.setRemoteDomainControllerIgnoreUnaffectedConfiguration(true);
                }
            } else {
                hostControllerInfo.setRemoteDomainControllerIgnoreUnaffectedConfiguration(ignoreUnusedConfiguration.asBoolean());
            }
            AdminOnlyDomainConfigPolicy domainConfigPolicy
                    = AdminOnlyDomainConfigPolicy.getPolicy(ADMIN_ONLY_POLICY.resolveModelAttribute(context, remoteDC).asString());
            hostControllerInfo.setAdminOnlyDomainConfigPolicy(domainConfigPolicy);
            overallConfigPersister.initializeDomainConfigurationPersister(true);

            domainController.initializeSlaveDomainRegistry(rootRegistration, overallConfigPersister.getDomainPersister(), contentRepository, remoteFileRepository,
                    hostControllerInfo, extensionRegistry, ignoredDomainResourceRegistry, pathManager);
        }
    }

    private static class TestLocalDomainControllerAddHandler extends DomainControllerWriteAttributeHandler {

        @Override
        protected void initializeLocalDomain() {
        }

        @Override
        protected void initializeRemoteDomain(OperationContext context, ModelNode remoteDC) throws OperationFailedException {
        }

    }

}
