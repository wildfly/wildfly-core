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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
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
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.service.ServiceController.Mode;
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
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, MANAGEMENT_IDENTITY_CAPABILITY, false)
            .build();

    public static final StringListAttributeDefinition INFLOW_SECURITY_DOMAINS = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.INFLOW_SECURITY_DOMAINS)
            .setAllowNull(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, MANAGEMENT_IDENTITY_CAPABILITY, false)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {SECURITY_DOMAIN, INFLOW_SECURITY_DOMAINS};

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
            // TODO Ekytron, this is probably way more complex than is really needed.

            String securityDomain = SECURITY_DOMAIN.resolveModelAttribute(context, model).asString();
            final InjectedValue<SecurityDomain> securityDomainInjected = new InjectedValue<>();

            final List<FutureTask<SecurityDomain>> inflowSecurityDomainFutures;

            IdentityService service = new IdentityService();

            ServiceBuilder<Void> serviceBuilder = context.getServiceTarget().addService(MANAGEMENT_IDENTITY_RUNTIME_CAPABILITY.getCapabilityServiceName(), service)
                    .setInitialMode(Mode.ACTIVE);

            serviceBuilder.addDependency(context.getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(SECURITY_DOMAIN_CAPABILITY, securityDomain), SecurityDomain.class), SecurityDomain.class, securityDomainInjected);

            ModelNode inflowDomainsModel = INFLOW_SECURITY_DOMAINS.resolveModelAttribute(context, model);
            if (inflowDomainsModel.isDefined()) {
                List<ModelNode> inflowDomainList = inflowDomainsModel.asList();
                inflowSecurityDomainFutures = new ArrayList<>(inflowDomainList.size());
                for (ModelNode current : inflowDomainList) {
                    InjectedValue<SecurityDomain> inflowInjected = new InjectedValue<>();
                    serviceBuilder.addDependency(
                            context.getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(
                                    SECURITY_DOMAIN_CAPABILITY, current.asString()), SecurityDomain.class),
                            SecurityDomain.class, inflowInjected);
                    inflowSecurityDomainFutures.add(toFutureTask(inflowInjected));
                }

            } else {
                inflowSecurityDomainFutures = Collections.emptyList();
            }

            List<FutureTask<SecurityDomain>> futureTasks = new ArrayList<>(inflowSecurityDomainFutures.size() + 1);
            final FutureTask<SecurityDomain> configuredSecurityDomainFuture = toFutureTask(securityDomainInjected);
            futureTasks.add(configuredSecurityDomainFuture);
            futureTasks.addAll(inflowSecurityDomainFutures);

            service.setGetSecurityDomainFutures(futureTasks);
            serviceBuilder.install();

            securityIdentitySupplier.setConfiguredSecurityDomainSupplier(() -> toSecurityDomain(configuredSecurityDomainFuture));

            if (inflowSecurityDomainFutures.size() > 0) {
                securityIdentitySupplier.setInflowSecurityDomainSuppliers(inflowSecurityDomainFutures.stream()
                        .map(f -> (Supplier<SecurityDomain>) (() -> toSecurityDomain(f))).collect(Collectors.toList()));
            }
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

    }

    private static FutureTask<SecurityDomain> toFutureTask(InjectedValue<SecurityDomain> injectedValue) {
        return new FutureTask<>((Callable<SecurityDomain>)  (() -> injectedValue.getValue()));
    }

    private static SecurityDomain toSecurityDomain(final FutureTask<SecurityDomain> futureTask) {
        try {
            return futureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    static class IdentityService implements Service<Void> {

        private List<FutureTask<SecurityDomain>> getSecurityDomainFutures = Collections.emptyList();

        @Override
        public void start(StartContext context) throws StartException {
            if (getSecurityDomainFutures != null) {
                getSecurityDomainFutures.forEach(FutureTask::run);
            }
        }

        @Override
        public void stop(StopContext context) {
        }

        void setGetSecurityDomainFutures(List<FutureTask<SecurityDomain>> getSecurityDomainFutures) {
            this.getSecurityDomainFutures = getSecurityDomainFutures;
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
