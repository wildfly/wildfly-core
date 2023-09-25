/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMESPACES;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * Handler for the root resource remove-namespace operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class NamespaceRemoveHandler implements OperationStepHandler {

    private static final String OPERATION_NAME = "remove-namespace";

    private static final SimpleAttributeDefinition NAMESPACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAMESPACE, ModelType.STRING)
            .setRequired(true)
            .setValidator(new ModelTypeValidator(ModelType.STRING, false))
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver("namespaces"))
            .setParameters(NAMESPACE)
            .build();


    public static final NamespaceRemoveHandler INSTANCE = new NamespaceRemoveHandler();

    /**
     * Create the RemoveNamespaceHandler
     */
    private NamespaceRemoveHandler() {
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();

        ModelNode param = NAMESPACE.resolveModelAttribute(context, operation);
        ModelNode namespaces = model.get(NAMESPACES);
        Property toRemove = null;
        ModelNode newList = new ModelNode().setEmptyList();
        String prefix = param.asString();
        if (namespaces.isDefined()) {
            for (Property namespace : namespaces.asPropertyList()) {
                if (prefix.equals(namespace.getName())) {
                    toRemove = namespace;
                } else {
                    newList.add(namespace.getName(), namespace.getValue());
                }
            }
        }

        if (toRemove != null) {
            namespaces.set(newList);
        } else {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.namespaceNotFound(prefix));
        }
    }
}
