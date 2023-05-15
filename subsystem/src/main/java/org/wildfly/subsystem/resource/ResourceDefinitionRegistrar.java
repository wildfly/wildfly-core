/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Registration interface for a {@link org.jboss.as.controller.ResourceDefinition}.
 * @author Paul Ferraro
 */
public interface ResourceDefinitionRegistrar<P> {

    /**
     * Registers a {@link  org.jboss.as.controller.ResourceDefinition} with the specified parent registration using the specified context.
     * @param parent the parent registration
     * @param context the registration context
     * @return the resource registration
     */
    ManagementResourceRegistration register(P parent, ManagementResourceRegistrationContext context);
}
