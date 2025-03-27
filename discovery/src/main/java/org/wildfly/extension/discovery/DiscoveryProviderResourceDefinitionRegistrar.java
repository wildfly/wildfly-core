/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.discovery;

import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Abstract registrar for a discovery provider resource definition.
 * @author Paul Ferraro
 */
public abstract class DiscoveryProviderResourceDefinitionRegistrar implements ChildResourceDefinitionRegistrar, ResourceServiceConfigurator, ResourceModelResolver<ServiceDependency<DiscoveryProvider>> {

    // TODO Move this to an SPI module, when this capability acquires any consumers
    static final UnaryServiceDescriptor<DiscoveryProvider> DISCOVERY_PROVIDER_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.discovery.provider", DiscoveryProvider.class);
    static final RuntimeCapability<Void> DISCOVERY_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(DISCOVERY_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    private final ResourceRegistration registration;
    private final Collection<AttributeDefinition> attributes;

    DiscoveryProviderResourceDefinitionRegistrar(ResourceRegistration registration, Collection<AttributeDefinition> attributes) {
        this.registration = registration;
        this.attributes = attributes;
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDescriptor descriptor = ResourceDescriptor.builder(DiscoverySubsystemResourceDefinitionRegistrar.RESOLVER.createChildResolver(this.registration.getPathElement()))
                .addAttributes(this.attributes)
                .addCapability(DISCOVERY_PROVIDER_CAPABILITY)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(this))
                .build();
        ResourceDefinition definition = ResourceDefinition.builder(this.registration, descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration registration = parent.registerSubModel(definition);

        ManagementResourceRegistrar.of(descriptor).register(registration);

        return registration;
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        return CapabilityServiceInstaller.builder(DiscoveryProviderResourceDefinitionRegistrar.DISCOVERY_PROVIDER_CAPABILITY, this.resolve(context, model)).build();
    }
}