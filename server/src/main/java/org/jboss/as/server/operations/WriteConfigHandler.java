package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.dmr.ModelNode;

/**
 * A {@link org.jboss.as.controller.OperationStepHandler} that can write current configuration without making any actual config change.
 */
public class WriteConfigHandler implements OperationStepHandler {

    protected static final String OPERATION_NAME = "write-config";

    public static final WriteConfigHandler INSTANCE = new WriteConfigHandler();

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ServerDescriptions.getResourceDescriptionResolver())
            .setRuntimeOnly()
            .build();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
    }
}
