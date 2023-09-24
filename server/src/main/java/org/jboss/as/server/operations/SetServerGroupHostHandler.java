/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 * Handler for operation run by Host Controller during boot to pass in the server group and host name.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SetServerGroupHostHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "set-server-group-host";

    public static final OperationDefinition DEFINITION =
            new SimpleOperationDefinitionBuilder(OPERATION_NAME, ServerDescriptions.getResourceDescriptionResolver("server"))
                    .setParameters(ServerRootResourceDefinition.SERVER_GROUP, ServerRootResourceDefinition.HOST)
                    .setPrivateEntry()
                    .build();

    public static final SetServerGroupHostHandler INSTANCE = new SetServerGroupHostHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (!context.isBooting()) {
            throw new IllegalStateException();
        }

        ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();

        ServerRootResourceDefinition.SERVER_GROUP.validateAndSet(operation, model);
        ServerRootResourceDefinition.HOST.validateAndSet(operation, model);
    }
}
