/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 *
 */
package org.jboss.as.controller.operations.common;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

/**
 * Reads the server state.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ProcessStateAttributeHandler implements OperationStepHandler {

    private static final String RUNTIME_CONFIGURATION_STATE = "runtime-configuration-state";

    private final ControlledProcessState processState;

    public ProcessStateAttributeHandler(final ControlledProcessState processState) {
        this.processState = processState;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        String name = operation.get(NAME).asString();
        // WFCORE-43 introduces a new attribute "runtime-configuration-state"
        // rather than introduce a new value into the ControlledProcessState enum, we map the new value here
        // this could be add in a future major release to be part of the enum.
        if (RUNTIME_CONFIGURATION_STATE.equals(name)) {
            if (processState.getState() == ControlledProcessState.State.RUNNING) {
                context.getResult().set(ClientConstants.CONTROLLER_PROCESS_STATE_OK);
                return;
            }
        }
        context.getResult().set(processState.getState().toString());
    }

}
