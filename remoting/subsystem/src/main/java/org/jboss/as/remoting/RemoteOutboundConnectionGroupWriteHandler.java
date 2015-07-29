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

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

import java.util.List;

/**
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
class RemoteOutboundConnectionGroupWriteHandler extends AbstractWriteAttributeHandler<Boolean> {

    static final RemoteOutboundConnectionGroupWriteHandler INSTANCE = new RemoteOutboundConnectionGroupWriteHandler();

    private RemoteOutboundConnectionGroupWriteHandler() {
        super(RemoteOutboundConnectionResourceDefinition.ATTRIBUTE_DEFINITIONS);
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Boolean> handbackHolder) throws OperationFailedException {
        final ModelNode model = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
        final boolean handback = applyModelToRuntime(context, operation, model);
        handbackHolder.setHandback(handback);
        return handback;

    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Boolean handback) throws OperationFailedException {
        if (handback != null && !handback.booleanValue()) {
            final ModelNode restored = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
            restored.get(attributeName).set(valueToRestore);
            applyModelToRuntime(context, operation, restored);
        }
    }

    private boolean applyModelToRuntime(OperationContext context, ModelNode operation, ModelNode fullModel)
            throws OperationFailedException {
        final ServiceRegistry registry = context.getServiceRegistry(true);
        final String groupName = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR)).getLastElement()
                .getValue();
        final int connectionsCount = RemoteOutboundConnectionGroupResourceDefinition.OUTBOUND_SOCKET_BINDINGS_REFS.resolveModelAttribute(context, fullModel)
                .asList().size();
        final List<ServiceName> serviceNames = RemotingServices.createConnectionServiceNames(groupName,
                connectionsCount);
        boolean reloadRequired = false;
        for (final ServiceName serviceName: serviceNames) {
            final ServiceController sc = registry.getService(serviceName);
            if (sc != null && sc.getState() == ServiceController.State.UP) {
                reloadRequired = true;
                break;
            }
        }
        if (!reloadRequired) {
            for (final ServiceName serviceName : serviceNames) {
                context.removeService(serviceName);
            }
            RemoteOutboundConnectionGroupAdd.INSTANCE.installRuntimeService(context, operation, fullModel);
        }
        return reloadRequired;
    }
}
