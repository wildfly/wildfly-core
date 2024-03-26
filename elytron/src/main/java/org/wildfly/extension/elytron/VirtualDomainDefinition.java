/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.VIRTUAL_SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.VIRTUAL_SECURITY_DOMAIN_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.DomainDefinition.OUTFLOW_ANONYMOUS;
import static org.wildfly.extension.elytron.DomainDefinition.outflow;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.INITIAL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.security.VirtualDomainMetaData;
import org.jboss.as.server.security.VirtualDomainMetaDataService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * A {@link ResourceDefinition} for a virtual security domain.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
class VirtualDomainDefinition extends SimpleResourceDefinition {

    static final StringListAttributeDefinition OUTFLOW_SECURITY_DOMAINS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.OUTFLOW_SECURITY_DOMAINS)
            .setRequired(false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, VIRTUAL_SECURITY_DOMAIN_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition AUTH_METHOD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTH_METHOD, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(VirtualDomainMetaData.AuthMethod.OIDC.toString()))
            .setValidator(EnumValidator.create(VirtualDomainMetaData.AuthMethod.class))
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { OUTFLOW_SECURITY_DOMAINS, OUTFLOW_ANONYMOUS, AUTH_METHOD };

    private static final VirtualDomainAddHandler ADD = new VirtualDomainAddHandler();
    private static final OperationStepHandler REMOVE = new VirtualDomainRemoveHandler(ADD);
    private static final WriteAttributeHandler WRITE = new WriteAttributeHandler(ElytronDescriptionConstants.VIRTUAL_SECURITY_DOMAIN);

    VirtualDomainDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.VIRTUAL_SECURITY_DOMAIN),
                ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.VIRTUAL_SECURITY_DOMAIN))
            .setAddHandler(ADD)
            .setRemoveHandler(REMOVE)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(VIRTUAL_SECURITY_DOMAIN_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }
    }

    private static ServiceController<VirtualDomainMetaData> installInitialService(OperationContext context, ServiceName initialName,
                                                                                  UnaryOperator<SecurityIdentity> identityOperator,
                                                                                  VirtualDomainMetaData.AuthMethod authMethod) throws OperationFailedException {
        ServiceTarget serviceTarget = context.getServiceTarget();
        VirtualDomainMetaDataService virtualDomainService = new VirtualDomainMetaDataService(identityOperator, authMethod);
        ServiceBuilder<VirtualDomainMetaData> virtualDomainBuilder = serviceTarget.addService(initialName, virtualDomainService)
                .setInitialMode(Mode.LAZY);
        commonDependencies(virtualDomainBuilder);
        return virtualDomainBuilder.install();
    }

    private static ServiceController<VirtualDomainMetaData> installService(OperationContext context, ServiceName virtualDomainName, ModelNode model) throws OperationFailedException {
        ServiceName initialName = virtualDomainName.append(INITIAL);

        final InjectedValue<VirtualDomainMetaData> virtualDomain = new InjectedValue<>();

        List<String> outflowSecurityDomainNames = OUTFLOW_SECURITY_DOMAINS.unwrap(context, model);
        final boolean outflowAnonymous = OUTFLOW_ANONYMOUS.resolveModelAttribute(context, model).asBoolean();
        final String authMethod = AUTH_METHOD.resolveModelAttribute(context, model).asString();
        final List<InjectedValue<SecurityDomain>> outflowSecurityDomainInjectors = new ArrayList<>(outflowSecurityDomainNames.size());
        final Set<SecurityDomain> outflowSecurityDomains = new HashSet<>();

        installInitialService(context, initialName,
                ! outflowSecurityDomainNames.isEmpty() ? i -> outflow(i, outflowAnonymous, outflowSecurityDomains) : UnaryOperator.identity(),
                VirtualDomainMetaData.AuthMethod.forName(authMethod));

        TrivialService<VirtualDomainMetaData> finalVirtualDomainService = new TrivialService<>();
        finalVirtualDomainService.setValueSupplier(new ValueSupplier<VirtualDomainMetaData>() {

            @Override
            public VirtualDomainMetaData get() throws StartException {
                for (InjectedValue<SecurityDomain> i : outflowSecurityDomainInjectors) {
                    outflowSecurityDomains.add(i.getValue());
                }
                return virtualDomain.getValue();
            }
        });

        ServiceTarget serviceTarget = context.getServiceTarget();
        ServiceBuilder<VirtualDomainMetaData> virtualDomainBuilder = serviceTarget.addService(virtualDomainName, finalVirtualDomainService)
                .setInitialMode(Mode.ACTIVE);
        virtualDomainBuilder.addDependency(initialName, VirtualDomainMetaData.class, virtualDomain);
        for (String outflowDomainName : outflowSecurityDomainNames) {
            InjectedValue<SecurityDomain> outflowDomainInjector = new InjectedValue<>();
            virtualDomainBuilder.addDependency(context.getCapabilityServiceName(SECURITY_DOMAIN_CAPABILITY, outflowDomainName, SecurityDomain.class).append(INITIAL), SecurityDomain.class, outflowDomainInjector);
            outflowSecurityDomainInjectors.add(outflowDomainInjector);
        }

        // This depends on the initial service which depends on the common dependencies so no need to add them for this one.
        return virtualDomainBuilder.install();
    }

    private static class VirtualDomainAddHandler extends BaseAddHandler {

        private VirtualDomainAddHandler() {
            super(VIRTUAL_SECURITY_DOMAIN_RUNTIME_CAPABILITY);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            RuntimeCapability<Void> runtimeCapability = VIRTUAL_SECURITY_DOMAIN_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName virtualDomainName = runtimeCapability.getCapabilityServiceName(VirtualDomainMetaData.class);
            installService(context, virtualDomainName, model);
        }
    }

    private static class VirtualDomainRemoveHandler extends TrivialCapabilityServiceRemoveHandler {

        VirtualDomainRemoveHandler(AbstractAddStepHandler addHandler) {
            super(addHandler, VIRTUAL_SECURITY_DOMAIN_RUNTIME_CAPABILITY);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
            super.performRuntime(context, operation, model);
            if (context.isResourceServiceRestartAllowed()) {
                final PathAddress address = context.getCurrentAddress();
                final String name = address.getLastElement().getValue();
                context.removeService(serviceName(name, address).append(INITIAL));
            }
        }
    }

    private static class WriteAttributeHandler extends ElytronRestartParentWriteAttributeHandler {

        WriteAttributeHandler(String parentKeyName) {
            super(parentKeyName, ATTRIBUTES);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress pathAddress) {
            return VIRTUAL_SECURITY_DOMAIN_RUNTIME_CAPABILITY.fromBaseCapability(pathAddress.getLastElement().getValue()).getCapabilityServiceName(VirtualDomainMetaData.class);
        }

        @Override
        protected void removeServices(final OperationContext context, final ServiceName parentService, final ModelNode parentModel) throws OperationFailedException {
            // WFCORE-2632, just for security-domain, remove also service with initial suffix.
            context.removeService(parentService.append(INITIAL));
            super.removeServices(context, parentService, parentModel);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel)
                throws OperationFailedException {
            installService(context, getParentServiceName(parentAddress), parentModel);
        }

    }
}
