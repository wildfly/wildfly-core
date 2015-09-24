/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.resource;

import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a resource representing a socket binding group.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class AbstractSocketBindingGroupResourceDefinition extends SimpleResourceDefinition {

    public static final String SOCKET_BINDING_GROUP_CAPABILITY_NAME = "org.wildfly.domain.socket-binding-group";
    public static final RuntimeCapability SOCKET_BINDING_GROUP_CAPABILITY = RuntimeCapability.Builder.of(SOCKET_BINDING_GROUP_CAPABILITY_NAME, true)
            .build();

    // Common attributes

    public static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SOCKET_BINDING_GROUP);

    public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING, false)
            .setResourceOnly()
            .setValidator(new StringLengthValidator(1)).build();

    public static final SimpleAttributeDefinition DEFAULT_INTERFACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.DEFAULT_INTERFACE, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setCapabilityReference("org.wildfly.network.interface", SOCKET_BINDING_GROUP_CAPABILITY)
            .build();

    private final List<AccessConstraintDefinition> accessConstraints;

    public AbstractSocketBindingGroupResourceDefinition(final OperationStepHandler addHandler, final OperationStepHandler removeHandler) {
        super(new Parameters(PATH, ControllerResolver.getResolver(ModelDescriptionConstants.SOCKET_BINDING_GROUP))
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES));
        this.accessConstraints = SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG.wrapAsList();
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);

        resourceRegistration.registerReadOnlyAttribute(NAME, null);
        resourceRegistration.registerReadWriteAttribute(DEFAULT_INTERFACE, null, new ReloadRequiredWriteAttributeHandler(DEFAULT_INTERFACE) {
            protected void validateUpdatedModel(final OperationContext context, final Resource model) throws OperationFailedException {
                validateDefaultInterfaceReference(context, model.getModel());
            }
        });

    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return accessConstraints;
    }

    public static void validateDefaultInterfaceReference(final OperationContext context, final ModelNode bindingGroup) throws OperationFailedException {

        ModelNode defaultInterfaceNode = bindingGroup.get(DEFAULT_INTERFACE.getName());
        if (defaultInterfaceNode.getType() == ModelType.STRING) { // ignore UNDEFINED and EXPRESSION
            String defaultInterface = defaultInterfaceNode.asString();
            PathAddress operationAddress = context.getCurrentAddress();
            //This can be used on both the host and the server, the socket binding group will be a
            //sibling to the interface in the model
            PathAddress interfaceAddress = PathAddress.EMPTY_ADDRESS;
            for (PathElement element : operationAddress) {
                if (element.getKey().equals(ModelDescriptionConstants.SOCKET_BINDING_GROUP)) {
                    break;
                }
                interfaceAddress = interfaceAddress.append(element);
            }
            interfaceAddress = interfaceAddress.append(ModelDescriptionConstants.INTERFACE, defaultInterface);
            try {
                context.readResourceFromRoot(interfaceAddress, false);
            } catch (RuntimeException e) {
                throw ControllerLogger.ROOT_LOGGER.nonexistentInterface(defaultInterface, DEFAULT_INTERFACE.getName());
            }
        }

    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerCapability(SOCKET_BINDING_GROUP_CAPABILITY);
    }
}
