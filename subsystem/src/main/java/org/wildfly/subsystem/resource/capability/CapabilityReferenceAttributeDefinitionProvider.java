/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import org.wildfly.subsystem.resource.AttributeDefinitionProvider;

/**
 * Provides an {@link org.jboss.as.controller.AttributeDefinition} that references a capability.
 */
public interface CapabilityReferenceAttributeDefinitionProvider<T> extends AttributeDefinitionProvider {

    CapabilityReferenceResolver<T> getCapabilityReferenceResolver();
}
