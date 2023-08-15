/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.wildfly.extension.elytron.common.ElytronCommonTrivialResourceDefinition;

/**
 * A trivial {@link ResourceDefinition}. This is a compatibility wrapper, not required by new usages of the
 * corresponding common class.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 *
 * @implNote This wrapper mirrors all methods from superclass to avoid issues with downcasting. Other implementations
 * can use {@link ElytronCommonTrivialResourceDefinition} directly.
 */
final class TrivialResourceDefinition extends ElytronCommonTrivialResourceDefinition {

    private TrivialResourceDefinition(String pathKey, ResourceDescriptionResolver resourceDescriptionResolver, AbstractAddStepHandler add, AbstractRemoveStepHandler remove, AttributeDefinition[] attributes,
                              Map<AttributeDefinition, OperationStepHandler> readOnlyAttributes, Map<OperationDefinition, OperationStepHandler> operations, List<ResourceDefinition> children,
                              RuntimeCapability<?>[] runtimeCapabilities) {
        super(pathKey, resourceDescriptionResolver, add, remove, attributes, readOnlyAttributes, operations, children, runtimeCapabilities);
    }

    TrivialResourceDefinition(String pathKey, AbstractAddStepHandler add, AttributeDefinition[] attributes, RuntimeCapability<?>... runtimeCapabilities) {
        super(ElytronExtension.class, pathKey, add, attributes, runtimeCapabilities);
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder extends ElytronCommonTrivialResourceDefinition.Builder {
        Builder() {
            super(ElytronExtension.class);
        }

        @Override
        protected Builder setPathKey(String pathKey) {
            return (Builder) super.setPathKey(pathKey);
        }

        @Override
        protected Builder setResourceDescriptionResolver(ResourceDescriptionResolver resourceDescriptionResolver) {
            return (Builder) super.setResourceDescriptionResolver(resourceDescriptionResolver);
        }

        @Override
        protected Builder setAddHandler(AbstractAddStepHandler addHandler) {
            return (Builder) super.setAddHandler(addHandler);
        }

        @Override
        protected Builder setRemoveHandler(AbstractRemoveStepHandler removeHandler) {
            return (Builder) super.setRemoveHandler(removeHandler);
        }

        @Override
        protected Builder setAttributes(AttributeDefinition... attributes) {
            return (Builder) super.setAttributes(attributes);
        }

        @Override
        protected Builder addReadOnlyAttribute(AttributeDefinition attribute, OperationStepHandler handler) {
            return (Builder) super.addReadOnlyAttribute(attribute, handler);
        }

        @Override
        protected Builder addOperation(OperationDefinition operation, OperationStepHandler handler) {
            return (Builder) super.addOperation(operation, handler);
        }

        @Override
        protected Builder setRuntimeCapabilities(RuntimeCapability<?>... runtimeCapabilities) {
            return (Builder) super.setRuntimeCapabilities(runtimeCapabilities);
        }

        @Override
        protected Builder addChild(ResourceDefinition child) {
            return (Builder) super.addChild(child);
        }

        @Override
        protected ResourceDefinition build() {
            return super.build();
        }
    }
}
