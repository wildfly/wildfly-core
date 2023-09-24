/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.dmr.ModelNode;

/**
 * Operation handlers responsible for putting the server in either the reload or restart-required state.
 *
 * @author Emanuel Muckenhuber
 */
public class ServerProcessStateHandler implements OperationStepHandler {

    public static final String REQUIRE_RELOAD_OPERATION = "server-set-reload-required";
    public static final String REQUIRE_RESTART_OPERATION = "server-set-restart-required";

    public static final SimpleOperationDefinition RELOAD_DEFINITION = new SimpleOperationDefinitionBuilder(REQUIRE_RELOAD_OPERATION, ServerDescriptions.getResourceDescriptionResolver())
            .withFlag(OperationEntry.Flag.HIDDEN)
            .build();

    public static final SimpleOperationDefinition RESTART_DEFINITION = new SimpleOperationDefinitionBuilder(REQUIRE_RESTART_OPERATION, ServerDescriptions.getResourceDescriptionResolver())
            .withFlag(OperationEntry.Flag.HIDDEN)
            .build();

    public static final OperationStepHandler SET_RELOAD_REQUIRED_HANDLER = new ServerProcessStateHandler(true);
    public static final OperationStepHandler SET_RESTART_REQUIRED_HANDLER = new ServerProcessStateHandler(false);

    private final boolean reload;
    ServerProcessStateHandler(boolean reload) {
        this.reload = reload;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.addStep(this::doExecute, OperationContext.Stage.RUNTIME);
    }

    private void doExecute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // Acquire the lock and check the write permissions for this operation
        context.getServiceRegistry(true);
        if (reload) {
            context.reloadRequired();
        } else {
            context.restartRequired();
        }
        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                if (reload) {
                    context.revertReloadRequired();
                } else {
                    context.revertRestartRequired();
                }
            }
        });
    }

}
