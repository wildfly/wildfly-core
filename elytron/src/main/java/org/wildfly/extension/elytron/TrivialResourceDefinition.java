/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.version.Stability;

/**
 * A trivial {@link ResourceDefinition}
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class TrivialResourceDefinition extends SimpleResourceDefinition {

    private final AttributeDefinition[] attributes;
    private final Map<OperationDefinition, OperationStepHandler> operations;
    private final Map<AttributeDefinition, OperationStepHandler> readOnlyAttributes;
    private final List<ResourceDefinition> children;
    private final String dependencyPackageName;

    TrivialResourceDefinition(String pathKey, ResourceDescriptionResolver resourceDescriptionResolver, AbstractAddStepHandler add, AttributeDefinition[] attributes, RuntimeCapability<?> ... runtimeCapabilities) {
        this(pathKey, resourceDescriptionResolver, add, new TrivialCapabilityServiceRemoveHandler(add, runtimeCapabilities), attributes, null, null, null, runtimeCapabilities, Stability.DEFAULT);
    }

    TrivialResourceDefinition(String pathKey, AbstractAddStepHandler add, AttributeDefinition[] attributes, RuntimeCapability<?> ... runtimeCapabilities) {
        this(pathKey, ElytronExtension.getResourceDescriptionResolver(pathKey), add, new TrivialCapabilityServiceRemoveHandler(add, runtimeCapabilities), attributes, null, null, null, runtimeCapabilities, Stability.DEFAULT);
    }

    private TrivialResourceDefinition(String pathKey, ResourceDescriptionResolver resourceDescriptionResolver, AbstractAddStepHandler add, AbstractRemoveStepHandler remove, AttributeDefinition[] attributes,
                                      Map<AttributeDefinition, OperationStepHandler> readOnlyAttributes, Map<OperationDefinition, OperationStepHandler> operations, List<ResourceDefinition> children,
                                      RuntimeCapability<?>[] runtimeCapabilities, Stability stability) {
        this(pathKey, resourceDescriptionResolver, add, remove, attributes, readOnlyAttributes, operations, children, runtimeCapabilities, stability, null);
    }

    private TrivialResourceDefinition(String pathKey, ResourceDescriptionResolver resourceDescriptionResolver, AbstractAddStepHandler add, AbstractRemoveStepHandler remove, AttributeDefinition[] attributes,
            Map<AttributeDefinition, OperationStepHandler> readOnlyAttributes, Map<OperationDefinition, OperationStepHandler> operations, List<ResourceDefinition> children,
            RuntimeCapability<?>[] runtimeCapabilities, Stability stability, String dependencyPackageName) {
        super(new Parameters(ResourceRegistration.of(PathElement.pathElement(pathKey), stability),
                resourceDescriptionResolver)
            .setAddHandler(add)
            .setRemoveHandler(remove)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(runtimeCapabilities));

        this.attributes = attributes;
        this.readOnlyAttributes = readOnlyAttributes;
        this.operations = operations;
        this.children = children;
        this.dependencyPackageName = dependencyPackageName;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
         if (attributes != null && attributes.length > 0) {
             for (AttributeDefinition current : attributes) {
                 resourceRegistration.registerReadWriteAttribute(current, null, ElytronReloadRequiredWriteAttributeHandler.INSTANCE);
             }
         }

         if (readOnlyAttributes != null) {
             for (Entry<AttributeDefinition, OperationStepHandler> entry : readOnlyAttributes.entrySet()) {
                 resourceRegistration.registerReadOnlyAttribute(entry.getKey(), entry.getValue());
             }
         }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        if (operations != null) {
            for (Entry<OperationDefinition, OperationStepHandler> entry : operations.entrySet()) {
                resourceRegistration.registerOperationHandler(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        if (children != null) {
            for (ResourceDefinition child : children) {
                resourceRegistration.registerSubModel(child);
            }
        }
    }

    @Override
    public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
        if (dependencyPackageName != null) {
            resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required(dependencyPackageName));
        }
    }

    public AttributeDefinition[] getAttributes() {
        return attributes;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String pathKey;
        private ResourceDescriptionResolver resourceDescriptionResolver;
        private AbstractAddStepHandler addHandler;
        private AbstractRemoveStepHandler removeHandler;
        private AttributeDefinition[] attributes;
        private Map<AttributeDefinition, OperationStepHandler> readOnlyAttributes;
        private Map<OperationDefinition, OperationStepHandler> operations;
        private RuntimeCapability<?>[] runtimeCapabilities;
        private List<ResourceDefinition> children;
        private Stability stability = Stability.DEFAULT;
        private String dependencyPackageName;

        Builder() {}

        Builder setPathKey(String pathKey) {
            this.pathKey = pathKey;

            return this;
        }

        Builder setResourceDescriptionResolver(ResourceDescriptionResolver resourceDescriptionResolver) {
            this.resourceDescriptionResolver = resourceDescriptionResolver;

            return this;
        }

        Builder setAddHandler(AbstractAddStepHandler addHandler) {
            this.addHandler = addHandler;

            return this;
        }

        Builder setRemoveHandler(AbstractRemoveStepHandler removeHandler) {
            this.removeHandler = removeHandler;

            return this;
        }

        Builder setAttributes(AttributeDefinition ... attributes) {
            this.attributes = attributes;

            return this;
        }

        Builder addReadOnlyAttribute(AttributeDefinition attribute, OperationStepHandler handler) {
            if (readOnlyAttributes == null) {
                readOnlyAttributes = new HashMap<>();
            }
            readOnlyAttributes.put(attribute, handler);

            return this;
        }

        Builder addOperation(OperationDefinition operation, OperationStepHandler handler) {
            if (operations == null) {
                operations = new HashMap<>();
            }
            operations.put(operation, handler);

            return this;
        }

        Builder setRuntimeCapabilities(RuntimeCapability<?> ... runtimeCapabilities) {
            this.runtimeCapabilities = runtimeCapabilities;

            return this;
        }

        Builder setStability(Stability stability) {
            this.stability = stability;
            return this;
        }

        Builder addChild(ResourceDefinition child) {
            if (children == null) {
                children = new ArrayList<>();
            }

            children.add(child);

            return this;
        }

        Builder setDependencyPackageName(String dependencyPackageName) {
            this.dependencyPackageName = dependencyPackageName;
            return this;
        }

        ResourceDefinition build() {
            ResourceDescriptionResolver resourceDescriptionResolver = this.resourceDescriptionResolver != null ? this.resourceDescriptionResolver : ElytronExtension.getResourceDescriptionResolver(pathKey);
            return new TrivialResourceDefinition(pathKey, resourceDescriptionResolver, addHandler,
                    removeHandler != null ? removeHandler : new TrivialCapabilityServiceRemoveHandler(addHandler, runtimeCapabilities),
                    attributes, readOnlyAttributes, operations, children, runtimeCapabilities, stability, dependencyPackageName);
        }

    }
}
