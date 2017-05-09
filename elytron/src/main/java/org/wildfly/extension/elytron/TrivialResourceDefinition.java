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
package org.wildfly.extension.elytron;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.msc.service.ServiceName;

/**
 * A trivial {@link ResourceDefinition}
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class TrivialResourceDefinition extends SimpleResourceDefinition {

    private final String pathKey;
    private final RuntimeCapability<?> firstCapability;
    private final AttributeDefinition[] attributes;

    TrivialResourceDefinition(String pathKey, ResourceDescriptionResolver resourceDescriptionResolver, AbstractAddStepHandler add, AttributeDefinition[] attributes, RuntimeCapability<?> ... runtimeCapabilities) {
        super(new Parameters(PathElement.pathElement(pathKey),
                resourceDescriptionResolver)
            .setAddHandler(add)
            .setRemoveHandler(new TrivialCapabilityServiceRemoveHandler(add, runtimeCapabilities))
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(runtimeCapabilities));

        this.pathKey = pathKey;
        this.firstCapability = runtimeCapabilities[0];
        this.attributes = attributes;
    }

    TrivialResourceDefinition(String pathKey, AbstractAddStepHandler add, AttributeDefinition[] attributes, RuntimeCapability<?> ... runtimeCapabilities) {
        this(pathKey, ElytronExtension.getResourceDescriptionResolver(pathKey), add, attributes, runtimeCapabilities);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
         if (attributes != null && attributes.length > 0) {
             WriteAttributeHandler restartParentWriteHandler = new WriteAttributeHandler(pathKey, attributes);
             ReloadRequiredWriteAttributeHandler reloadRequiredWriteHandler = new ReloadRequiredWriteAttributeHandler(attributes);
             for (AttributeDefinition current : attributes) {
                 boolean restartAll = current.getFlags().contains(AttributeAccess.Flag.RESTART_ALL_SERVICES);
                 resourceRegistration.registerReadWriteAttribute(current, null, restartAll ? reloadRequiredWriteHandler : restartParentWriteHandler);
             }
         }
    }

    private class WriteAttributeHandler extends ElytronRestartParentWriteAttributeHandler {

        WriteAttributeHandler(String parentName, AttributeDefinition ... attributes) {
            super(parentName, attributes);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress pathAddress) {
            return firstCapability.fromBaseCapability(pathAddress.getLastElement().getValue()).getCapabilityServiceName();
        }
    }

}
