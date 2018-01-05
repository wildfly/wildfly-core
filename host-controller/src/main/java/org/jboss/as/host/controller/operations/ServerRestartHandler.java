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


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.Locale;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Restarts a server.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerRestartHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "restart";
    private static final AttributeDefinition SUSPEND_TIMEOUT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SUSPEND_TIMEOUT, ModelType.INT, true)
            .setMeasurementUnit(MeasurementUnit.SECONDS).setDefaultValue(new ModelNode(0)).build();

    public static final OperationDefinition DEFINITION = ServerStartHandler.getOperationDefinition(OPERATION_NAME, ServerStartHandler.START_MODE, SUSPEND_TIMEOUT);

    private final ServerInventory serverInventory;

    /**
     * Create the ServerRestartHandler
     */
    public ServerRestartHandler(final ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.cannotStartServersInvalidMode(context.getRunningMode()));
        }

        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        final PathElement element = address.getLastElement();
        final String serverName = element.getValue();
        final boolean blocking = operation.get(ModelDescriptionConstants.BLOCKING).asBoolean(false);
        final boolean suspend = ServerStartHandler.START_MODE.resolveModelAttribute(context, operation).asString().toLowerCase(Locale.ENGLISH).equals(ServerStartHandler.StartMode.SUSPEND.toString());
        final int gracefulTimeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt(); //Timeout in seconds

        final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // WFLY-2189 trigger a write-runtime authz check
                context.getServiceRegistry(true);

                final ServerStatus origStatus = serverInventory.determineServerStatus(serverName);
                if (origStatus != ServerStatus.STARTED) {
                    throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.cannotRestartServer(serverName, origStatus));
                }
                final ServerStatus status = serverInventory.restartServer(serverName, gracefulTimeout, model, blocking, suspend);
                context.getResult().set(status.toString());
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
