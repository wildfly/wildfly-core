/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.elytron.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;

/**
 * A trivial {@link ResourceDefinition}
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public class ElytronCommonTrivialResourceDefinition extends SimpleResourceDefinition {

    private final AttributeDefinition[] attributes;
    private final Map<OperationDefinition, OperationStepHandler> operations;
    private final Map<AttributeDefinition, OperationStepHandler> readOnlyAttributes;
    private final List<ResourceDefinition> children;

    protected ElytronCommonTrivialResourceDefinition(String pathKey, ResourceDescriptionResolver resourceDescriptionResolver, AbstractAddStepHandler add, AbstractRemoveStepHandler remove, AttributeDefinition[] attributes,
            Map<AttributeDefinition, OperationStepHandler> readOnlyAttributes, Map<OperationDefinition, OperationStepHandler> operations, List<ResourceDefinition> children,
            RuntimeCapability<?>[] runtimeCapabilities) {
        super(new Parameters(PathElement.pathElement(pathKey),
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
    }

    protected ElytronCommonTrivialResourceDefinition(String pathKey, ResourceDescriptionResolver resourceDescriptionResolver, AbstractAddStepHandler add, AttributeDefinition[] attributes, RuntimeCapability<?> ... runtimeCapabilities) {
        this(pathKey, resourceDescriptionResolver, add, new TrivialCapabilityServiceRemoveHandler(add, runtimeCapabilities), attributes, null, null, null, runtimeCapabilities);
    }

    protected ElytronCommonTrivialResourceDefinition(final Class<?> extensionClass, String pathKey, AbstractAddStepHandler add, AttributeDefinition[] attributes, RuntimeCapability<?> ... runtimeCapabilities) {
        this(pathKey, ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass, pathKey), add, new TrivialCapabilityServiceRemoveHandler(add, runtimeCapabilities), attributes, null, null, null, runtimeCapabilities);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
         if (attributes != null && attributes.length > 0) {
             AbstractWriteAttributeHandler writeHandler = new ElytronReloadRequiredWriteAttributeHandler(attributes);
             for (AttributeDefinition current : attributes) {
                 resourceRegistration.registerReadWriteAttribute(current, null, writeHandler);
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

    public AttributeDefinition[] getAttributes() {
        return attributes;
    }

    protected static Builder getCommonBuilder(final Class<?> extensionClass) {
        return new Builder(extensionClass);
    }

    protected static class Builder {

        private final Class<?> extensionClass;

        private String pathKey;
        private ResourceDescriptionResolver resourceDescriptionResolver;
        private AbstractAddStepHandler addHandler;
        private AbstractRemoveStepHandler removeHandler;
        private AttributeDefinition[] attributes;
        private Map<AttributeDefinition, OperationStepHandler> readOnlyAttributes;
        private Map<OperationDefinition, OperationStepHandler> operations;
        private RuntimeCapability<?>[] runtimeCapabilities;
        private List<ResourceDefinition> children;

        protected Builder(final Class<?> extensionClass) {
            this.extensionClass = extensionClass;
        }

        protected Builder setPathKey(String pathKey) {
            this.pathKey = pathKey;

            return this;
        }

        protected Builder setResourceDescriptionResolver(ResourceDescriptionResolver resourceDescriptionResolver) {
            this.resourceDescriptionResolver = resourceDescriptionResolver;

            return this;
        }

        protected Builder setAddHandler(AbstractAddStepHandler addHandler) {
            this.addHandler = addHandler;

            return this;
        }

        protected Builder setRemoveHandler(AbstractRemoveStepHandler removeHandler) {
            this.removeHandler = removeHandler;

            return this;
        }

        protected Builder setAttributes(AttributeDefinition ... attributes) {
            this.attributes = attributes;

            return this;
        }

        protected Builder addReadOnlyAttribute(AttributeDefinition attribute, OperationStepHandler handler) {
            if (readOnlyAttributes == null) {
                readOnlyAttributes = new HashMap<>();
            }
            readOnlyAttributes.put(attribute, handler);

            return this;
        }

        protected Builder addOperation(OperationDefinition operation, OperationStepHandler handler) {
            if (operations == null) {
                operations = new HashMap<>();
            }
            operations.put(operation, handler);

            return this;
        }

        protected Builder setRuntimeCapabilities(RuntimeCapability<?> ... runtimeCapabilities) {
            this.runtimeCapabilities = runtimeCapabilities;

            return this;
        }

        protected Builder addChild(ResourceDefinition child) {
            if (children == null) {
                children = new ArrayList<>();
            }

            children.add(child);

            return this;
        }

        protected ResourceDefinition build() {
            ResourceDescriptionResolver resourceDescriptionResolver = this.resourceDescriptionResolver != null ? this.resourceDescriptionResolver : ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass, pathKey);
            return new ElytronCommonTrivialResourceDefinition(pathKey, resourceDescriptionResolver, addHandler,
                    removeHandler != null ? removeHandler : new TrivialCapabilityServiceRemoveHandler(addHandler, runtimeCapabilities),
                    attributes, readOnlyAttributes, operations, children, runtimeCapabilities);
        }

    }
}
