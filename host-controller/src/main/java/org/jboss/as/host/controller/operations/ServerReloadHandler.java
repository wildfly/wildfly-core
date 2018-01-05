/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.operations;

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
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.host.controller.operations.ServerStartHandler.getOperationDefinition;

import java.util.Locale;

/**
 * @author Emanuel Muckenhuber
 */
public class ServerReloadHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "reload";
    private static final AttributeDefinition SUSPEND_TIMEOUT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SUSPEND_TIMEOUT, ModelType.INT, true)
            .setMeasurementUnit(MeasurementUnit.SECONDS).setDefaultValue(new ModelNode(0)).build();

    public static final OperationDefinition DEFINITION = getOperationDefinition(OPERATION_NAME, ServerStartHandler.START_MODE, SUSPEND_TIMEOUT);

    private final ServerInventory serverInventory;
    public ServerReloadHandler(ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

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
        final int gracefulTimeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                // WFLY-2189 trigger a write-runtime authz check
                context.getServiceRegistry(true);

                final ServerStatus status = serverInventory.reloadServer(serverName, blocking, suspend, gracefulTimeout);
                context.getResult().set(status.toString());
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
