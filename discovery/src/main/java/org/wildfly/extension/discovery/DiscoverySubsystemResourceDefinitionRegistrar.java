/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.SubsystemResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;

/**
 * Registrar for the discovery subsystem.
 * @author Paul Ferraro
 */
class DiscoverySubsystemResourceDefinitionRegistrar implements SubsystemResourceDefinitionRegistrar {

    static final SubsystemResourceRegistration REGISTRATION = SubsystemResourceRegistration.of("discovery");
    static final ParentResourceDescriptionResolver RESOLVER = new SubsystemResourceDescriptionResolver(REGISTRATION.getName(), DiscoverySubsystemResourceDefinitionRegistrar.class);

    @Override
    public ManagementResourceRegistration register(SubsystemRegistration parent, ManagementResourceRegistrationContext context) {
        parent.setHostCapable();

        ManagementResourceRegistration registration = parent.registerSubsystemModel(ResourceDefinition.builder(REGISTRATION, RESOLVER).build());
        ResourceDescriptor descriptor = ResourceDescriptor.builder(RESOLVER).build();

        ManagementResourceRegistrar.of(descriptor).register(registration);

        new AggregateDiscoveryProviderResourceDefinitionRegistrar().register(registration, context);
        new StaticDiscoveryProviderResourceDefinitionRegistrar().register(registration, context);

        return registration;
    }
}