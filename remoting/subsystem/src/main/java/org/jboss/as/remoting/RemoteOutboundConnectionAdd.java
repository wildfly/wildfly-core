/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.OptionMap;

/**
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class RemoteOutboundConnectionAdd extends AbstractAddStepHandler {

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(resource);
        installRuntimeService(context, fullModel);
    }

    static void installRuntimeService(final OperationContext context, final ModelNode fullModel) throws OperationFailedException {
        final String outboundSocketBindingRef = RemoteOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.resolveModelAttribute(context, fullModel).asString();
        final OptionMap connOpts = ConnectorUtils.getOptions(context, fullModel.get(CommonAttributes.PROPERTY));
        final String username = RemoteOutboundConnectionResourceDefinition.USERNAME.resolveModelAttribute(context, fullModel).asStringOrNull();
        final String authenticationContext = RemoteOutboundConnectionResourceDefinition.AUTHENTICATION_CONTEXT.resolveModelAttribute(context, fullModel).asStringOrNull();
        final String protocol = authenticationContext != null ? null : RemoteOutboundConnectionResourceDefinition.PROTOCOL.resolveModelAttribute(context, fullModel).asString();

        final CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addCapability(OUTBOUND_CONNECTION_CAPABILITY);
        final Consumer<RemoteOutboundConnectionService> serviceConsumer = builder.provides(OUTBOUND_CONNECTION_CAPABILITY);
        final Supplier<OutboundSocketBinding> osbSupplier = builder.requiresCapability(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, OutboundSocketBinding.class, outboundSocketBindingRef);
        final Supplier<AuthenticationContext> acSupplier = (authenticationContext != null) ? builder.requiresCapability(AUTHENTICATION_CONTEXT_CAPABILITY, AuthenticationContext.class, authenticationContext) : null;
        builder.requiresCapability(RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY.getName(), Endpoint.class);
        builder.setInstance(new RemoteOutboundConnectionService(serviceConsumer, osbSupplier, acSupplier, connOpts, username, protocol));
        builder.install();
    }
}
