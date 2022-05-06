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

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.host.controller.HostControllerConfigurationPersister;
import org.jboss.as.host.controller.discovery.StaticDiscovery;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.model.host.AdminOnlyDomainConfigPolicy;
import org.jboss.as.remoting.Protocol;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public abstract class DomainControllerWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    public static final SimpleAttributeDefinition AUTHENTICATION_CONTEXT =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.AUTHENTICATION_CONTEXT,  ModelType.STRING,  true)
                    .setCapabilityReference("org.wildfly.security.authentication-context", "org.wildfly.host.controller")
                    .setAlternatives(ModelDescriptionConstants.SECURITY_REALM, ModelDescriptionConstants.USERNAME)
                    .build();
    public static final SimpleAttributeDefinition PORT =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT, ModelType.INT)
                    .setRequired(false)
                    .setAllowExpression(true)
                    .setValidator(new IntRangeValidator(1, 65535, true, true))
                    .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                    .setRequires(ModelDescriptionConstants.HOST)
                    .build();
    public static final SimpleAttributeDefinition USERNAME =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.USERNAME, ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .setFlags(AttributeAccess.Flag.RESTART_JVM)
                    .setAlternatives(ModelDescriptionConstants.AUTHENTICATION_CONTEXT)
                    .setDeprecated(ModelVersion.create(5))
                    .build();
    public static final SimpleAttributeDefinition ADMIN_ONLY_POLICY =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ADMIN_ONLY_POLICY, ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_JVM)
                    .setValidator(EnumValidator.create(AdminOnlyDomainConfigPolicy.class))
                    .setDefaultValue(new ModelNode(AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG.toString()))
                    .setAllowedValues(AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG.toString(), AdminOnlyDomainConfigPolicy.FETCH_FROM_DOMAIN_CONTROLLER.toString(), AdminOnlyDomainConfigPolicy.REQUIRE_LOCAL_CONFIG.toString())
                    .build();
    public static final SimpleAttributeDefinition IGNORE_UNUSED_CONFIG =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.IGNORE_UNUSED_CONFIG, ModelType.BOOLEAN, true)
                    .setRequired(false)
                    .setAllowExpression(true)
                    .setFlags(AttributeAccess.Flag.RESTART_JVM)
                    .build();
    public static final SimpleAttributeDefinition HOST =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HOST, ModelType.STRING)
                    .setRequired(false)
                    .setAllowExpression(true)
                    .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
                    .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                    .setRequires(ModelDescriptionConstants.PORT)
                    .build();
    public static final SimpleAttributeDefinition PROTOCOL =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PROTOCOL, ModelType.STRING)
                    .setRequired(false)
                    .setAllowExpression(true)
                    .setValidator(EnumValidator.create(Protocol.class))
                    .setDefaultValue(org.jboss.as.remoting.Protocol.REMOTE.toModelNode())
                    .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                    .setRequires(ModelDescriptionConstants.HOST, ModelDescriptionConstants.PORT)
                    .build();

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
                initializeLocalDomain(null);
            }
        } else if (operation.hasDefined(VALUE, REMOTE)) {
            if (dc.has(LOCAL)) {
                dc.remove(LOCAL);
            }
            final ModelNode remoteDC = dc.get(REMOTE);
            if (remoteDC.hasDefined(ADMIN_ONLY_POLICY.getName())) {
                ModelNode current = ADMIN_ONLY_POLICY.resolveModelAttribute(context, remoteDC);
                if (current.asString().equals(AdminOnlyDomainConfigPolicy.LEGACY_FETCH_FROM_DOMAIN_CONTROLLER.toString())) {
                    ControllerLogger.ROOT_LOGGER.adminOnlyPolicyDeprecatedValue();
                    remoteDC.get(ADMIN_ONLY_POLICY.getName()).set(AdminOnlyDomainConfigPolicy.FETCH_FROM_DOMAIN_CONTROLLER.toString());
                }
            }
            secureRemoteDomain(context, operation, remoteDC);
            if (context.isBooting()) {
                initializeRemoteDomain(context, remoteDC);
            }
        }
    }

    /**
     *
     * @param localHostNameOverride - if this is not null this name will be used to override either the default, computed host name (see {@link org.jboss.as.host.controller.HostControllerEnvironment})
     *                              or the configured name from host.xml (see {@link org.jboss.as.host.controller.parsing.HostXml_6}). If this value is null, then no override is present.
     */
    abstract void initializeLocalDomain(final String localHostNameOverride);

    abstract void secureRemoteDomain(OperationContext context, ModelNode operation, ModelNode remoteDC) throws OperationFailedException;

    abstract void initializeRemoteDomain(OperationContext context, ModelNode remoteDC) throws OperationFailedException;

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
        void initializeLocalDomain(final String localHostNameOverride) {
            hostControllerInfo.setMasterDomainController(true);
            if (localHostNameOverride != null) {
                hostControllerInfo.setLocalHostName(localHostNameOverride);
            }
            if (localHostNameOverride == null) {
                // for adding a host later, we don't use the domain persister
                overallConfigPersister.initializeDomainConfigurationPersister(false);
            }
            domainController.initializeMasterDomainRegistry(rootRegistration, localHostNameOverride == null ? overallConfigPersister.getDomainPersister() : null,
                    contentRepository, localFileRepository, extensionRegistry, pathManager);
        }

        @Override
        void initializeRemoteDomain(OperationContext context, ModelNode remoteDC) throws OperationFailedException {
            hostControllerInfo.setMasterDomainController(false);
            ModelNode protocolNode = DomainControllerWriteAttributeHandler.PROTOCOL.resolveModelAttribute(context, remoteDC);
            ModelNode hostNode = DomainControllerWriteAttributeHandler.HOST.resolveModelAttribute(context, remoteDC);
            ModelNode portNode = DomainControllerWriteAttributeHandler.PORT.resolveModelAttribute(context, remoteDC);
            if (hostNode.isDefined() && portNode.isDefined()) {
                String host = hostNode.asString();
                int port = portNode.asInt();
                String protocol = protocolNode.asString();
                StaticDiscovery staticDiscoveryOption = new StaticDiscovery(protocol, host, port);
                hostControllerInfo.addRemoteDomainControllerDiscoveryOption(staticDiscoveryOption);
            }
            ModelNode usernameNode = DomainControllerWriteAttributeHandler.USERNAME.resolveModelAttribute(context, remoteDC);
            if (usernameNode.isDefined()) {
                hostControllerInfo.setRemoteDomainControllerUsername(usernameNode.asString());
            }

            ModelNode ignoreUnusedConfiguration = DomainControllerWriteAttributeHandler.IGNORE_UNUSED_CONFIG.resolveModelAttribute(context, remoteDC);

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
                    = AdminOnlyDomainConfigPolicy.getPolicy(DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY.resolveModelAttribute(context, remoteDC).asString());
            hostControllerInfo.setAdminOnlyDomainConfigPolicy(domainConfigPolicy);
            overallConfigPersister.initializeDomainConfigurationPersister(true);

            domainController.initializeSlaveDomainRegistry(rootRegistration, overallConfigPersister.getDomainPersister(), contentRepository, remoteFileRepository,
                    hostControllerInfo, extensionRegistry, ignoredDomainResourceRegistry, pathManager);
        }

        @Override
        void secureRemoteDomain(OperationContext context, ModelNode operation, ModelNode remoteDC) throws OperationFailedException {
            ModelNode parameters = operation.get(VALUE, REMOTE);
            if (parameters.has(AUTHENTICATION_CONTEXT.getName())) {
                AUTHENTICATION_CONTEXT.validateAndSet(parameters, remoteDC);
                final String authenticationContext = AUTHENTICATION_CONTEXT.resolveModelAttribute(context, parameters).asString();
                context.addStep(new OperationStepHandler() {

                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        hostControllerInfo.setAuthenticationContext(context.getCapabilityServiceName(
                                "org.wildfly.security.authentication-context", authenticationContext, AuthenticationContext.class));
                    }
                }, Stage.RUNTIME);
            } else {
                remoteDC.get(DomainControllerWriteAttributeHandler.AUTHENTICATION_CONTEXT.getName()).clear();
            }
        }

    }
    private static class TestLocalDomainControllerAddHandler extends DomainControllerWriteAttributeHandler {

        @Override
        void initializeLocalDomain(final String hostName) {
        }

        @Override
        void initializeRemoteDomain(OperationContext context, ModelNode remoteDC) throws OperationFailedException {
        }

        @Override
        void secureRemoteDomain(OperationContext context, ModelNode operation, ModelNode remoteDC) throws OperationFailedException {
        }

    }

}
