/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.EnumSet;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Stops a server.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerStopHandler implements OperationStepHandler {

    private static final SimpleAttributeDefinition TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TIMEOUT, ModelType.INT)
            .setDefaultValue(new ModelNode(0))
            .setValidator(new IntRangeValidator(-1))
            .setAllowNull(true)
            .build();

    public static final String OPERATION_NAME = "stop";
    public static final OperationDefinition DEFINITION = ServerStartHandler.getOperationDefinition(OPERATION_NAME, TIMEOUT);


    private final ServerInventory serverInventory;

    /**
     * Create the ServerAddHandler
     */
    public ServerStopHandler(final ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        final PathElement element = address.getLastElement();
        final String serverName = element.getValue();
        final boolean blocking = operation.get(BLOCKING).asBoolean(false);
        final int timeout = TIMEOUT.resolveModelAttribute(context, operation).asInt();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // WFLY-2741 -- DO NOT call context.getServiceRegistry(true) as that will trigger blocking for
                // service container stability and one use case for this op is to recover from a
                // messed up service container from a previous op. Instead just ask for authorization.
                // Note that we already have the exclusive lock, so we are just skipping waiting for stability.
                // If another op that is a step in a composite step with this op needs to modify the container
                // it will have to wait for container stability, so skipping this only matters for the case
                // where this step is the only runtime change.
                context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME));

                final ServerStatus status = serverInventory.stopServer(serverName, timeout, blocking);
                try {
                    context.readResource(PathAddress.EMPTY_ADDRESS, false); //reading the resource to persist the autostart state.
                } catch (Resource.NoSuchResourceException ex) {
                    //in case the resource no longer exists.
                }
                context.getResult().set(status.toString());
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
