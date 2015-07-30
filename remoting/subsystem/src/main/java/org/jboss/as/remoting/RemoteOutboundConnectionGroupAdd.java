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

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.Endpoint;
import org.xnio.OptionMap;

import java.util.List;

/**
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
class RemoteOutboundConnectionGroupAdd extends AbstractAddStepHandler {

    static final RemoteOutboundConnectionGroupAdd INSTANCE = new RemoteOutboundConnectionGroupAdd();

    private RemoteOutboundConnectionGroupAdd() {
        super(RemoteOutboundConnectionGroupResourceDefinition.ATTRIBUTE_DEFINITIONS);
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final Resource resource)
            throws OperationFailedException {
        performRuntime(context, operation, Resource.Tools.readModel(resource));
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode fullModel)
            throws OperationFailedException {
        installRuntimeService(context, operation, fullModel);
    }

    void installRuntimeService(final OperationContext context, final ModelNode operation, final ModelNode fullModel)
            throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));

        final String groupName = address.getLastElement().getValue();

        final List<ModelNode> socketRefs = RemoteOutboundConnectionGroupResourceDefinition.OUTBOUND_SOCKET_BINDINGS_REFS
                .resolveModelAttribute(context, fullModel).asList();

        final String protocol = RemoteOutboundConnectionGroupResourceDefinition.PROTOCOL.resolveModelAttribute(context,
                operation).asString();
        final String username = RemoteOutboundConnectionGroupResourceDefinition.USERNAME.resolveModelAttribute(context,
                fullModel).asString();
        final String securityRealm = fullModel.hasDefined(CommonAttributes.SECURITY_REALM) ? fullModel.require(
                CommonAttributes.SECURITY_REALM).asString() : null;

        final OptionMap connectionCreationOptions = ConnectorUtils
                .getOptions(context, fullModel.get(CommonAttributes.PROPERTY));

        for (int i = 0; i < socketRefs.size(); i++) {
            final String socketRef = socketRefs.get(i).asString();
            final String connectionName = new StringBuilder(groupName).append(i+1).toString();
            installConnectionService(context, socketRef, connectionName, username, protocol, securityRealm,
                    connectionCreationOptions);
        }
    }

    void installConnectionService(final OperationContext context, final String socketRef, final String connectionName,
            final String username, final String protocol, final String securityRealm, final OptionMap options) {
        final ServiceName outboundSocketBindingDependency = context.getCapabilityServiceName(
                AbstractOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, socketRef,
                OutboundSocketBinding.class);
        // create the service
        final RemoteOutboundConnectionService outboundConnectionService = new RemoteOutboundConnectionService(connectionName,
                options, username, protocol);
        final ServiceName serviceName = AbstractOutboundConnectionService.OUTBOUND_CONNECTION_BASE_SERVICE_NAME
                .append(connectionName);

        // also add an alias service name to easily distinguish between a generic, remote and local type of connection
        // services
        final ServiceName aliasServiceName = RemoteOutboundConnectionService.REMOTE_OUTBOUND_CONNECTION_BASE_SERVICE_NAME
                .append(connectionName);
        final ServiceBuilder<RemoteOutboundConnectionService> svcBuilder = context
                .getServiceTarget()
                .addService(serviceName, outboundConnectionService)
                .addAliases(aliasServiceName)
                .addDependency(RemotingServices.SUBSYSTEM_ENDPOINT, Endpoint.class,
                        outboundConnectionService.getEndpointInjector())
                .addDependency(outboundSocketBindingDependency, OutboundSocketBinding.class,
                        outboundConnectionService.getDestinationOutboundSocketBindingInjector());

        if (securityRealm != null) {
            SecurityRealm.ServiceUtil.addDependency(svcBuilder, outboundConnectionService.getSecurityRealmInjector(),
                    securityRealm, false);
        }
        svcBuilder.install();
    }
}
