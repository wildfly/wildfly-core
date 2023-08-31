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

import static org.jboss.as.remoting.AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY;
import static org.jboss.as.remoting.AbstractOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME;
import static org.jboss.as.remoting.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.OptionMap;

/**
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class RemoteOutboundConnectionAdd extends AbstractAddStepHandler {


    static final RemoteOutboundConnectionAdd INSTANCE = new RemoteOutboundConnectionAdd();

    private RemoteOutboundConnectionAdd() {
        super(RemoteOutboundConnectionResourceDefinition.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(resource);
        installRuntimeService(context, fullModel);
    }

    void installRuntimeService(final OperationContext context, final ModelNode fullModel) throws OperationFailedException {
        final String connectionName = context.getCurrentAddressValue();
        final String outboundSocketBindingRef = RemoteOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.resolveModelAttribute(context, fullModel).asString();
        final OptionMap connOpts = ConnectorUtils.getOptions(context, fullModel.get(CommonAttributes.PROPERTY));
        final String authenticationContext = RemoteOutboundConnectionResourceDefinition.AUTHENTICATION_CONTEXT.resolveModelAttribute(context, fullModel).asStringOrNull();

        // create the service
        final ServiceName serviceName = OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(connectionName);
        final ServiceName aliasServiceName = RemoteOutboundConnectionService.REMOTE_OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);
        final ServiceName deprecatedName = AbstractOutboundConnectionService.OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);

        final CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addCapability(OUTBOUND_CONNECTION_CAPABILITY);
        final Consumer<RemoteOutboundConnectionService> serviceConsumer = builder.provides(OUTBOUND_CONNECTION_CAPABILITY, serviceName, deprecatedName, aliasServiceName);
        final Supplier<OutboundSocketBinding> osbSupplier = builder.requiresCapability(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, OutboundSocketBinding.class, outboundSocketBindingRef);
        final Supplier<AuthenticationContext> acSupplier = (authenticationContext != null) ? builder.requiresCapability(AUTHENTICATION_CONTEXT_CAPABILITY, AuthenticationContext.class, authenticationContext) : null;
        builder.requires(RemotingServices.SUBSYSTEM_ENDPOINT);
        builder.setInstance(new RemoteOutboundConnectionService(serviceConsumer, osbSupplier, acSupplier, connOpts, null, null));
        builder.install();
    }
}
