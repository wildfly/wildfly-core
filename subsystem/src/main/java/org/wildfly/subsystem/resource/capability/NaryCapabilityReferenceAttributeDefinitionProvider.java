/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * A provider of an attribute definition that resolves to multiple service dependencies.
 */
public interface NaryCapabilityReferenceAttributeDefinitionProvider<T> extends CapabilityReferenceAttributeDefinitionProvider<T>, ResourceModelResolver<List<ServiceDependency<T>>> {

    @Override
    default List<ServiceDependency<T>> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        List<ModelNode> values = this.resolveModelAttribute(context, model).asListOrEmpty();
        if (values.isEmpty()) List.of();

        CapabilityReferenceResolver<T> resolver = this.getCapabilityReferenceResolver();
        List<ServiceDependency<T>> dependencies = new ArrayList<>(values.size());
        for (ModelNode value : values) {
            Map.Entry<String, String[]> resolved = resolver.resolve(context, resource, value.asString());
            if (resolved != null) {
                dependencies.add(ServiceDependency.on(resolved.getKey(), resolver.getRequirement().getType(), resolved.getValue()));
            }
        }
        return Collections.unmodifiableList(dependencies);
    }
}
