/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IDENTITY;

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
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.auth.server.SecurityDomain;

/**
 * A resource definition for the security domain to use for the identity for management operations and any additional security
 * domains to attempt inflow from.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
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
                .setRemoveHandler(new ReloadRequiredRemoveStepHandler(MANAGEMENT_IDENTITY_RUNTIME_CAPABILITY))
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
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
            super(MANAGEMENT_IDENTITY_RUNTIME_CAPABILITY, ATTRIBUTES);
            this.securityIdentitySupplier = securityIdentitySupplier;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            String securityDomain = SECURITY_DOMAIN.resolveModelAttribute(context, model).asString();
            final InjectedValue<SecurityDomain> securityDomainInjected = new InjectedValue<>();

            final IdentityService service = new IdentityService(securityIdentitySupplier);

            ServiceBuilder<Void> serviceBuilder = context.getServiceTarget().addService(MANAGEMENT_IDENTITY_RUNTIME_CAPABILITY.getCapabilityServiceName(), service)
                    .setInitialMode(Mode.ACTIVE);

            serviceBuilder.addDependency(context.getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(SECURITY_DOMAIN_CAPABILITY, securityDomain), SecurityDomain.class), SecurityDomain.class, securityDomainInjected);

            service.setConfiguredSecurityDomain(securityDomainInjected);
            serviceBuilder.install();
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

    static class IdentityService implements Service<Void> {

        private InjectedValue<SecurityDomain> configuredSecurityDomain = null;
        private final ManagementSecurityIdentitySupplier securityIdentitySupplier;

        public IdentityService(ManagementSecurityIdentitySupplier securityIdentitySupplier) {
            this.securityIdentitySupplier = securityIdentitySupplier;
        }

        @Override
        public void start(StartContext context) throws StartException {
             securityIdentitySupplier.setConfiguredSecurityDomainSupplier(configuredSecurityDomain::getValue);
        }

        @Override
        public void stop(StopContext context) {
            securityIdentitySupplier.setConfiguredSecurityDomainSupplier(null);
        }

        void setConfiguredSecurityDomain(InjectedValue<SecurityDomain> configuredSecurityDomain) {
            this.configuredSecurityDomain = configuredSecurityDomain;
        }

        @Override
        public Void getValue() throws IllegalStateException, IllegalArgumentException {
            return null;
        }
    }

    static class WriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

        public WriteAttributeHandler() {
            super(ATTRIBUTES);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return context.isBooting() == false;
        }

    }

}
