/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

/**
 * @author Jaikiran Pai
 */
class LocalOutboundConnectionAdd extends AbstractAddStepHandler {

    static final LocalOutboundConnectionAdd INSTANCE = new LocalOutboundConnectionAdd();

    private LocalOutboundConnectionAdd() {
        super(AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY,
                LocalOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
        installRuntimeService(context, operation, fullModel);
    }

    void installRuntimeService(final OperationContext context, final ModelNode operation,
                                            final ModelNode fullModel) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
        final String connectionName = address.getLastElement().getValue();
        final String outboundSocketBindingRef = LocalOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.resolveModelAttribute(context, operation).asString();
        final ServiceName outboundSocketBindingDependency = OutboundSocketBinding.OUTBOUND_SOCKET_BINDING_BASE_SERVICE_NAME.append(outboundSocketBindingRef);
        // fetch the connection creation options from the model
        final OptionMap connectionCreationOptions = ConnectorUtils.getOptions(context, fullModel.get(CommonAttributes.PROPERTY));
        // create the service
        final LocalOutboundConnectionService outboundConnectionService = new LocalOutboundConnectionService(connectionName, connectionCreationOptions);
        final ServiceName serviceName = AbstractOutboundConnectionService.OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);
        // also add an alias service name to easily distinguish between a generic, remote and local type of connection services
        final ServiceName aliasServiceName = LocalOutboundConnectionService.LOCAL_OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);
        final ServiceBuilder<LocalOutboundConnectionService> svcBuilder = context.getServiceTarget().addService(serviceName, outboundConnectionService)
                .addAliases(aliasServiceName)
                .addDependency(RemotingServices.SUBSYSTEM_ENDPOINT, Endpoint.class, outboundConnectionService.getEndpointInjector())
                .addDependency(outboundSocketBindingDependency, OutboundSocketBinding.class, outboundConnectionService.getDestinationOutboundSocketBindingInjector());

        svcBuilder.install();
    }
}
