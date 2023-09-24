/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.BootErrorCollector;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintUtilizationRegistry;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.access.AccessIdentityResourceDefinition;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.EnvironmentNameReader;
import org.jboss.as.domain.management.controller.ManagementControllerResourceDefinition;

/**
 * A {@link org.jboss.as.controller.ResourceDefinition} for the the core management resource.
 *
 * The content of this resource is dependent on the process it is being used within i.e. standalone server, host controller or
 * domain server.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class CoreManagementResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(CORE_SERVICE, MANAGEMENT);

    public static void registerDomainResource(Resource parent, AccessConstraintUtilizationRegistry registry) {
        Resource coreManagement = Resource.Factory.create();
        coreManagement.registerChild(AccessAuthorizationResourceDefinition.PATH_ELEMENT,
                AccessAuthorizationResourceDefinition.createResource(registry));
        parent.registerChild(PATH_ELEMENT, coreManagement);
    }

    private final Environment environment;
    private final List<ResourceDefinition> interfaces;
    private final DelegatingConfigurableAuthorizer authorizer;
    private final ManagementSecurityIdentitySupplier securityIdentitySupplier;
    private final ManagedAuditLogger auditLogger;
    private final PathManagerService pathManager;
    private final EnvironmentNameReader environmentReader;
    private final BootErrorCollector bootErrorCollector;

    private CoreManagementResourceDefinition(final Environment environment, final DelegatingConfigurableAuthorizer authorizer,
            final ManagementSecurityIdentitySupplier securityIdentitySupplier, final ManagedAuditLogger auditLogger,
            final PathManagerService pathManager, final EnvironmentNameReader environmentReader,
            final List<ResourceDefinition> interfaces, final BootErrorCollector bootErrorCollector) {
        super(PATH_ELEMENT, DomainManagementResolver.getResolver(CORE, MANAGEMENT));
        this.environment = environment;
        this.authorizer = authorizer;
        this.securityIdentitySupplier = securityIdentitySupplier;
        this.interfaces = interfaces;
        this.auditLogger = auditLogger;
        this.pathManager = pathManager;
        this.environmentReader = environmentReader;
        this.bootErrorCollector = bootErrorCollector;
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        if (environment != Environment.DOMAIN) {
            resourceRegistration.registerSubModel(ManagementControllerResourceDefinition.INSTANCE);
            // Configuration Changes
            resourceRegistration.registerSubModel(LegacyConfigurationChangeResourceDefinition.INSTANCE);
        }

        for (ResourceDefinition current : interfaces) {
            resourceRegistration.registerSubModel(current);
        }

        switch (environment) {
            case DOMAIN:
                resourceRegistration.registerSubModel(AccessAuthorizationResourceDefinition.forDomain(authorizer));
                resourceRegistration.registerSubModel(LegacyConfigurationChangeResourceDefinition.forDomain());
                break;
            case DOMAIN_SERVER:
                resourceRegistration.registerSubModel(AccessAuthorizationResourceDefinition.forDomainServer(authorizer));
                break;
            case STANDALONE_SERVER:
                resourceRegistration.registerSubModel(AccessAuthorizationResourceDefinition.forStandaloneServer(authorizer));
        }

        if (environment != Environment.DOMAIN) {
            resourceRegistration.registerSubModel(new AccessAuditResourceDefinition(auditLogger, pathManager, environmentReader));
            resourceRegistration.registerSubModel(AccessIdentityResourceDefinition.newInstance(securityIdentitySupplier));
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        if(bootErrorCollector != null) {
            resourceRegistration.registerOperationHandler(BootErrorCollector.ListBootErrorsHandler.DEFINITION, bootErrorCollector.getReadBootErrorsHandler());
        }
    }

    public static SimpleResourceDefinition forDomain(final DelegatingConfigurableAuthorizer authorizer, final ManagementSecurityIdentitySupplier securityIdentitySupplier) {
        List<ResourceDefinition> interfaces = Collections.emptyList();
        return new CoreManagementResourceDefinition(Environment.DOMAIN, authorizer, securityIdentitySupplier, null, null, null, interfaces, null);
    }

    public static SimpleResourceDefinition forDomainServer(final DelegatingConfigurableAuthorizer authorizer, final ManagementSecurityIdentitySupplier securityIdentitySupplier,
            final ManagedAuditLogger auditLogger, final PathManagerService pathManager, final EnvironmentNameReader environmentReader,
            final BootErrorCollector bootErrorCollector) {
        List<ResourceDefinition> interfaces = Collections.emptyList();
        return new CoreManagementResourceDefinition(Environment.DOMAIN_SERVER, authorizer, securityIdentitySupplier, auditLogger, pathManager, environmentReader, interfaces, bootErrorCollector);
    }

    public static SimpleResourceDefinition forHost(final DelegatingConfigurableAuthorizer authorizer, final ManagementSecurityIdentitySupplier securityIdentitySupplier,
            final ManagedAuditLogger auditLogger, final PathManagerService pathManager, final EnvironmentNameReader environmentReader,
            final BootErrorCollector bootErrorCollector, final ResourceDefinition... interfaces) {
        return new CoreManagementResourceDefinition(Environment.HOST_CONTROLLER, authorizer, securityIdentitySupplier, auditLogger, pathManager, environmentReader, Arrays.asList(interfaces), bootErrorCollector);
    }

    public static SimpleResourceDefinition forStandaloneServer(final DelegatingConfigurableAuthorizer authorizer, final ManagementSecurityIdentitySupplier securityIdentitySupplier,
            final ManagedAuditLogger auditLogger, final PathManagerService pathManager, final EnvironmentNameReader environmentReader,
            final BootErrorCollector bootErrorCollector, final ResourceDefinition... interfaces) {
        return new CoreManagementResourceDefinition(Environment.STANDALONE_SERVER, authorizer, securityIdentitySupplier, auditLogger, pathManager, environmentReader, Arrays.asList(interfaces), bootErrorCollector);
    }

}
