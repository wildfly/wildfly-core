/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.boot.cli.hook;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class BootCliShutdownHandler implements OperationStepHandler {

    public static final BootCliShutdownHandler INSTANCE = new BootCliShutdownHandler();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("shutdown", NonResolvingResourceDescriptionResolver.INSTANCE)
            .setRuntimeOnly()
            .build();
    Map<String, ModelNode> parameters;

    private BootCliShutdownHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        operation = operation.clone();
        operation.remove(OP);
        operation.remove(OP_ADDR);

        parameters = new HashMap<>();
        for (String key : operation.keys()) {
            parameters.put(key, operation.get(key));
        }
    }
}
