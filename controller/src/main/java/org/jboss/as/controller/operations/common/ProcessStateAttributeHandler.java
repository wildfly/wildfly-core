/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

/**
 *
 */
package org.jboss.as.controller.operations.common;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

/**
 * Reads the server state.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ProcessStateAttributeHandler implements OperationStepHandler {

    private static final String SERVER_STATE = "server-state";
    private static final String HOST_STATE = "host-state";

    private final ControlledProcessState processState;

    public ProcessStateAttributeHandler(final ControlledProcessState processState) {
        this.processState = processState;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // compatibility with host-state / running-state mapping the OK to RUNNING
        String name = operation.get(NAME).asString();
        // compatibility with old server-state / host-state, replaced with state.
        if (SERVER_STATE.equals(name) || HOST_STATE.equals(name)) {
            if (processState.getState() == ControlledProcessState.State.OK) {
                context.getResult().set(ControlledProcessState.State.RUNNING.toString());
                return;
            }
        }
        context.getResult().set(processState.getState().toString());
    }

}
