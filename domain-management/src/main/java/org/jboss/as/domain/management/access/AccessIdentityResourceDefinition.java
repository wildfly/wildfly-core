/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IDENTITY;

import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.management.ModelDescriptionConstants;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.server.SecurityDomain;

/**
 * A resource definition for the security domain to use for the identity for management operations and any additional security
 * domains to attempt inflow from.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class AccessIdentityResourceDefinition extends SimpleResourceDefinition {

    private static final String SECURITY_DOMAIN_CAPABILITY = "org.wildfly.security.security-domain";
    private static final String MANAGEMENT_IDENTITY_CAPABILITY = "org.wildfly.management.identity";
    private static final RuntimeCapability<Void> MANAGEMENT_IDENTITY_RUNTIME_CAPABILITY = RuntimeCapability.Builder.of(MANAGEMENT_IDENTITY_CAPABILITY, Void.class)
            .build();

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(ACCESS, IDENTITY);

    public static final SimpleAttributeDefinition SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SECURITY_DOMAIN, ModelType.STRING, false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, MANAGEMENT_IDENTITY_CAPABILITY)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.ELYTRON_SECURITY_DOMAIN_REF)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {SECURITY_DOMAIN};

    private AccessIdentityResourceDefinition(AbstractAddStepHandler add) {
        super(new Parameters(PATH_ELEMENT, DomainManagementResolver.getResolver("core.identity"))
                .setAddHandler(add)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(MANAGEMENT_IDENTITY_RUNTIME_CAPABILITY)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.ACCESS_CONTROL));
    }

    public static ResourceDefinition newInstance(final ManagementSecurityIdentitySupplier securityIdentitySupplier) {
        return new AccessIdentityResourceDefinition(new AccessIdentityAddHandler(securityIdentitySupplier));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        WriteAttributeHandler write = new WriteAttributeHandler();
        for (AttributeDefinition attribute : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, write);
        }
    }

    static class AccessIdentityAddHandler extends AbstractAddStepHandler {

        private final ManagementSecurityIdentitySupplier securityIdentitySupplier;

        AccessIdentityAddHandler(ManagementSecurityIdentitySupplier securityIdentitySupplier) {
            this.securityIdentitySupplier = securityIdentitySupplier;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            final String securityDomain = SECURITY_DOMAIN.resolveModelAttribute(context, model).asString();
            final ServiceBuilder<?> sb = context.getServiceTarget().addService(MANAGEMENT_IDENTITY_RUNTIME_CAPABILITY.getCapabilityServiceName());
            final Supplier<SecurityDomain> sdSupplier = sb.requires(context.getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(SECURITY_DOMAIN_CAPABILITY, securityDomain), SecurityDomain.class));
            sb.setInstance(new IdentityService(sdSupplier, securityIdentitySupplier));
            sb.install();
            //Let's verify that the IdentityService is correctly started.
            context.addStep((OperationContext context1, ModelNode operation1) -> {
                try {
                    ServiceController<?> controller = context1.getServiceRegistry(false).getRequiredService(MANAGEMENT_IDENTITY_RUNTIME_CAPABILITY.getCapabilityServiceName());
                    if (controller == null || State.UP != controller.getState()) {
                        context.setRollbackOnly();
                    }
                } catch (ServiceNotFoundException ex) {
                    context.setRollbackOnly();
                }
            }, OperationContext.Stage.VERIFY);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return (context.getProcessType() != ProcessType.EMBEDDED_SERVER
                    || context.getRunningMode() != RunningMode.ADMIN_ONLY)
                    && (context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER);
        }
    }

    private static final class IdentityService implements Service {
        private final Supplier<SecurityDomain> securityDomainSupplier;
        private final ManagementSecurityIdentitySupplier securityIdentitySupplier;

        private IdentityService(final Supplier<SecurityDomain> securityDomainSupplier,
                                final ManagementSecurityIdentitySupplier securityIdentitySupplier) {
            this.securityDomainSupplier = securityDomainSupplier;
            this.securityIdentitySupplier = securityIdentitySupplier;
        }

        @Override
        public void start(final StartContext context) {
             securityIdentitySupplier.setConfiguredSecurityDomainSupplier(securityDomainSupplier::get);
        }

        @Override
        public void stop(final StopContext context) {
            securityIdentitySupplier.setConfiguredSecurityDomainSupplier(null);
        }
    }

    static class WriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return context.isBooting() == false;
        }

    }

}
