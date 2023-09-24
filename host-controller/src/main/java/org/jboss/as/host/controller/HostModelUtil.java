/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller;


import org.jboss.as.controller.BootErrorCollector;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.common.ValidateOperationHandler;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.management.security.WhoAmIOperation;
import org.jboss.as.host.controller.descriptions.HostEnvironmentResourceDefinition;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.model.host.HostDefinition;
import org.jboss.as.host.controller.model.host.HostResourceDefinition;
import org.jboss.as.host.controller.operations.LocalDomainControllerAddHandler;
import org.jboss.as.host.controller.operations.LocalDomainControllerRemoveHandler;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.host.controller.operations.RemoteDomainControllerAddHandler;
import org.jboss.as.host.controller.operations.RemoteDomainControllerRemoveHandler;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import org.jboss.as.controller.operations.global.ReadFeatureDescriptionHandler;

import org.jboss.as.host.controller.operations.DomainControllerWriteAttributeHandler;

/**
 * Utility for creating the root element and populating the {@link org.jboss.as.controller.registry.ManagementResourceRegistration}
 * for an individual host's portion of the model.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:jperkins@jboss.com">James R. Perkins</a>
 */
public class HostModelUtil {

    public interface HostModelRegistrar {
        void registerHostModel(final String hostName, final ManagementResourceRegistration root);
    }

    public static StandardResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(HOST);
        for (String kp : keyPrefix) {
            prefix.append('.').append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), HostEnvironmentResourceDefinition.class.getPackage().getName() + ".LocalDescriptions", HostModelUtil.class.getClassLoader(), true, false);
    }


    public static void createRootRegistry(final ManagementResourceRegistration root,
                                          final HostControllerEnvironment environment,
                                          final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                                          final HostModelRegistrar hostModelRegistrar,
                                          final ProcessType processType,
                                          final DelegatingConfigurableAuthorizer authorizer,
                                          final Resource modelControllerResource,
                                          final LocalHostControllerInfoImpl localHostControllerInfo,
                                          final ImmutableCapabilityRegistry capabilityRegistry) {

        // register HostDefinition for /host=*:add()
        root.registerSubModel(new HostDefinition(root, environment, ignoredDomainResourceRegistry, hostModelRegistrar, processType, authorizer, modelControllerResource, localHostControllerInfo));

        // Global operations
        GlobalOperationHandlers.registerGlobalOperations(root, processType);
        root.registerOperationHandler(ReadFeatureDescriptionHandler.DEFINITION, ReadFeatureDescriptionHandler.getInstance(capabilityRegistry), true);
        // Global notifications
        GlobalNotifications.registerGlobalNotifications(root, processType);

        if (root.getOperationEntry(PathAddress.EMPTY_ADDRESS, ValidateOperationHandler.DEFINITION.getName())==null){//this is hack
            root.registerOperationHandler(ValidateOperationHandler.DEFINITION, ValidateOperationHandler.INSTANCE);
        }
        root.registerOperationHandler(WhoAmIOperation.DEFINITION, WhoAmIOperation.createOperation(authorizer), true);

        // Other root resource operations
        root.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);
    }

    public static ManagementResourceRegistration createHostRegistry(final String hostName,
                                          final ManagementResourceRegistration root, final HostControllerConfigurationPersister configurationPersister,
                                          final HostControllerEnvironment environment, final HostRunningModeControl runningModeControl,
                                          final HostFileRepository localFileRepository,
                                          final LocalHostControllerInfoImpl hostControllerInfo, final ServerInventory serverInventory,
                                          final HostFileRepository remoteFileRepository,
                                          final ContentRepository contentRepository,
                                          final DomainController domainController,
                                          final ExtensionRegistry hostExtensionRegistry,
                                          final ExtensionRegistry extensionRegistry,
                                          final IgnoredDomainResourceRegistry ignoredRegistry,
                                          final ControlledProcessState processState,
                                          final PathManagerService pathManager,
                                          final DelegatingConfigurableAuthorizer authorizer,
                                          final ManagementSecurityIdentitySupplier securityIdentitySupplier,
                                          final ManagedAuditLogger auditLogger,
                                          final BootErrorCollector bootErrorCollector) {
        // Add of the host itself
        ManagementResourceRegistration hostRegistration = root.registerSubModel(
                new HostResourceDefinition(hostName, configurationPersister,
                        environment, runningModeControl, localFileRepository,
                        hostControllerInfo, serverInventory, remoteFileRepository,
                        contentRepository, domainController, hostExtensionRegistry,
                        ignoredRegistry, processState, pathManager, authorizer, securityIdentitySupplier, auditLogger, bootErrorCollector));

        final DomainControllerWriteAttributeHandler dcWAH =
                DomainControllerWriteAttributeHandler.getInstance(root, hostControllerInfo, configurationPersister,
                    localFileRepository, remoteFileRepository, contentRepository, domainController, extensionRegistry, ignoredRegistry, pathManager);
        hostRegistration.registerReadWriteAttribute(HostResourceDefinition.DOMAIN_CONTROLLER, null, dcWAH);
        //TODO See if some of all these parameters can come from domain controller
        LocalDomainControllerAddHandler localDcAddHandler = LocalDomainControllerAddHandler.getInstance(dcWAH);
        hostRegistration.registerOperationHandler(LocalDomainControllerAddHandler.DEFINITION, localDcAddHandler);
        hostRegistration.registerOperationHandler(LocalDomainControllerRemoveHandler.DEFINITION, LocalDomainControllerRemoveHandler.INSTANCE);
        RemoteDomainControllerAddHandler remoteDcAddHandler = new RemoteDomainControllerAddHandler(hostControllerInfo, dcWAH);
        hostRegistration.registerOperationHandler(RemoteDomainControllerAddHandler.DEFINITION, remoteDcAddHandler);
        hostRegistration.registerOperationHandler(RemoteDomainControllerRemoveHandler.DEFINITION, RemoteDomainControllerRemoveHandler.INSTANCE);
        return hostRegistration;
    }
}
