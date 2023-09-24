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
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
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
        super(RemoteOutboundConnectionResourceDefinition.ATTRIBUTE_DEFINITIONS);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(resource);
        installRuntimeService(context, fullModel);
    }

    void installRuntimeService(final OperationContext context, final ModelNode fullModel) throws OperationFailedException {
        final String connectionName = context.getCurrentAddressValue();
        final String outboundSocketBindingRef = RemoteOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.resolveModelAttribute(context, fullModel).asString();
        final ServiceName outboundSocketBindingDependency = context.getCapabilityServiceName(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, outboundSocketBindingRef, OutboundSocketBinding.class);
        final OptionMap connOpts = ConnectorUtils.getOptions(context, fullModel.get(CommonAttributes.PROPERTY));
        final String username = RemoteOutboundConnectionResourceDefinition.USERNAME.resolveModelAttribute(context, fullModel).asStringOrNull();
        final String securityRealm = RemoteOutboundConnectionResourceDefinition.SECURITY_REALM.resolveModelAttribute(context, fullModel).asStringOrNull();
        final String authenticationContext = RemoteOutboundConnectionResourceDefinition.AUTHENTICATION_CONTEXT.resolveModelAttribute(context, fullModel).asStringOrNull();
        final String protocol = authenticationContext != null ? null : RemoteOutboundConnectionResourceDefinition.PROTOCOL.resolveModelAttribute(context, fullModel).asString();

        // create the service
        final ServiceName serviceName = OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(connectionName);
        final ServiceName aliasServiceName = RemoteOutboundConnectionService.REMOTE_OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);
        final ServiceName deprecatedName = AbstractOutboundConnectionService.OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);

        final ServiceBuilder<?> builder = context.getServiceTarget().addService(serviceName);
        final Consumer<RemoteOutboundConnectionService> serviceConsumer = builder.provides(deprecatedName, aliasServiceName);
        final Supplier<OutboundSocketBinding> osbSupplier = builder.requires(outboundSocketBindingDependency);
        final Supplier<AuthenticationContext> acSupplier = authenticationContext != null ? builder.requires(context.getCapabilityServiceName(AUTHENTICATION_CONTEXT_CAPABILITY, authenticationContext, AuthenticationContext.class)) : null;
        builder.requires(RemotingServices.SUBSYSTEM_ENDPOINT);
        builder.setInstance(new RemoteOutboundConnectionService(serviceConsumer, osbSupplier, acSupplier, connOpts, username, protocol));
        builder.install();
    }
}
