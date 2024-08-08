/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * A provider of an attribute definition that resolves to a single service dependency.
 */
public interface UnaryCapabilityReferenceAttributeDefinitionProvider<T> extends CapabilityReferenceAttributeDefinitionProvider<T>, ResourceModelResolver<ServiceDependency<T>> {

    @Override
    default ServiceDependency<T> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        String value = this.resolveModelAttribute(context, model).asStringOrNull();
        CapabilityReferenceResolver<T> resolver = this.getCapabilityReferenceResolver();
        Map.Entry<String, String[]> resolved = resolver.resolve(context, resource, value);
        return (resolved != null) ? ServiceDependency.on(resolved.getKey(), resolver.getRequirement().getType(), resolved.getValue()) : ServiceDependency.of(null);
    }
}
