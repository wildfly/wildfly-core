/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SCHEMA_LOCATIONS;

import org.jboss.as.controller.logging.ControllerLogger;
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
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * Handler for the root resource remove-schema-location operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SchemaLocationRemoveHandler implements OperationStepHandler {

    private static final String OPERATION_NAME = "remove-schema-location";

    private static final SimpleAttributeDefinition URI = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.URI, ModelType.STRING)
            .setRequired(true)
            .setValidator(new ModelTypeValidator(ModelType.STRING, false))
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver("schema-locations"))
            .setParameters(URI)
            .build();

    public static final SchemaLocationRemoveHandler INSTANCE = new SchemaLocationRemoveHandler();

    public static ModelNode getRemoveSchemaLocationOperation(ModelNode address, String schemaURI) {
        ModelNode op = Util.createOperation(OPERATION_NAME, PathAddress.pathAddress(address));
        op.get(URI.getName()).set(schemaURI);
        return op;
    }

    /**
     * Create the RemoveSchemaLocationHandler
     */
    private SchemaLocationRemoveHandler() {
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
        ModelNode param = URI.resolveModelAttribute(context, operation);


        ModelNode locations = model.get(SCHEMA_LOCATIONS);
        Property toRemove = null;
        ModelNode newList = new ModelNode().setEmptyList();
        String uri = param.asString();
        if (locations.isDefined()) {
            for (Property location : locations.asPropertyList()) {
                if (uri.equals(location.getName())) {
                    toRemove = location;
                } else {
                    newList.add(location.getName(), location.getValue());
                }
            }
        }
        if (toRemove != null) {
            locations.set(newList);
        } else {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.schemaNotFound(uri));
        }
    }

}
