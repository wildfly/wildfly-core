/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.remoting;

import java.util.Set;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public class RemoteOutboundConnectionGroupRemove extends AbstractRemoveStepHandler {

    static final RemoteOutboundConnectionGroupRemove INSTANCE = new RemoteOutboundConnectionGroupRemove();

    protected RemoteOutboundConnectionGroupRemove() {
        super();
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {

        if (context.isResourceServiceRestartAllowed()) {
            final String groupName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR))
                    .getLastElement().getValue();
            final int connectionsCount = RemoteOutboundConnectionGroupResourceDefinition.OUTBOUND_SOCKET_BINDINGS_REFS
                    .resolveModelAttribute(context, model).asList().size();
            Set<ServiceName> connectionServiceNames = RemotingServices.createConnectionServiceNames(groupName,
                    connectionsCount);
            for (final ServiceName connectionServiceName : connectionServiceNames) {
                context.removeService(connectionServiceName);
            }
            final ServiceName groupServiceName = RemoteOutboundConnectionGroupResourceDefinition.OUTBOUND_CONNECTION_GROUP_BASE_SERVICE_NAME
                    .append(groupName);
            context.removeService(groupServiceName);
        } else {
            context.reloadRequired();
        }
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {
        if (context.isResourceServiceRestartAllowed()) {
            RemoteOutboundConnectionGroupAdd.INSTANCE.performRuntime(context, operation, model);
        } else {
            context.revertReloadRequired();
        }
    }
}
