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

import static org.jboss.as.remoting.AbstractOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME;
import static org.jboss.as.remoting.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;

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
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.OptionMap;

/**
 * @author Jaikiran Pai
 */
class RemoteOutboundConnectionAdd extends AbstractAddStepHandler {

    static final RemoteOutboundConnectionAdd INSTANCE = new RemoteOutboundConnectionAdd();

    private RemoteOutboundConnectionAdd() {
        super(RemoteOutboundConnectionResourceDefinition.ATTRIBUTE_DEFINITIONS);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(resource);
        installRuntimeService(context, operation, fullModel);
    }

    void installRuntimeService(final OperationContext context, final ModelNode operation, final ModelNode fullModel) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
        final String connectionName = address.getLastElement().getValue();

        final String outboundSocketBindingRef = RemoteOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.resolveModelAttribute(context, operation).asString();
        final String protocol = RemoteOutboundConnectionResourceDefinition.PROTOCOL.resolveModelAttribute(context, operation).asString();
        final ServiceName outboundSocketBindingDependency = context.getCapabilityServiceName(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, outboundSocketBindingRef, OutboundSocketBinding.class);
        // fetch the connection creation options from the model
        final OptionMap connectionCreationOptions = ConnectorUtils.getOptions(context, fullModel.get(CommonAttributes.PROPERTY));
        final String username = RemoteOutboundConnectionResourceDefinition.USERNAME.resolveModelAttribute(context, fullModel).asStringOrNull();
        final String securityRealm = fullModel.hasDefined(CommonAttributes.SECURITY_REALM) ? fullModel.require(CommonAttributes.SECURITY_REALM).asString() : null;
        final String authenticationContext = fullModel.hasDefined(CommonAttributes.AUTHENTICATION_CONTEXT) ? fullModel.require(CommonAttributes.AUTHENTICATION_CONTEXT).asString() : null;

        // create the service
        final RemoteOutboundConnectionService outboundConnectionService = new RemoteOutboundConnectionService(connectionCreationOptions, username, protocol);
        final ServiceName serviceName = AbstractOutboundConnectionService.OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);
        // also add an alias service name to easily distinguish between a generic, remote and local type of connection services
        final ServiceName aliasServiceName = RemoteOutboundConnectionService.REMOTE_OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);
        final ServiceBuilder<RemoteOutboundConnectionService> svcBuilder = context.getServiceTarget().addService(serviceName, outboundConnectionService)
                .addAliases(aliasServiceName)
                .addDependency(RemotingServices.SUBSYSTEM_ENDPOINT, Endpoint.class, outboundConnectionService.getEndpointInjector())
                .addDependency(outboundSocketBindingDependency, OutboundSocketBinding.class, outboundConnectionService.getDestinationOutboundSocketBindingInjector());

        if (securityRealm != null) {
            SecurityRealm.ServiceUtil.addDependency(svcBuilder, outboundConnectionService.getSecurityRealmInjector(), securityRealm);
        }
        if (authenticationContext != null) {
            svcBuilder.addDependency(
                    context.getCapabilityServiceName(AUTHENTICATION_CONTEXT_CAPABILITY, authenticationContext, AuthenticationContext.class),
                    AuthenticationContext.class, outboundConnectionService.getAuthenticationContextInjector());
        }
        svcBuilder.install();

    }
}
