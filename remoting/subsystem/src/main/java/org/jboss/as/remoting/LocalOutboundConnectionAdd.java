/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.remoting.AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY;
import static org.jboss.as.remoting.AbstractOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME;

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

/**
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class LocalOutboundConnectionAdd extends AbstractAddStepHandler {

    static final LocalOutboundConnectionAdd INSTANCE = new LocalOutboundConnectionAdd();

    private LocalOutboundConnectionAdd() {
        super(LocalOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        installRuntimeService(context, resource.getModel());
    }

    void installRuntimeService(final OperationContext context, final ModelNode model) throws OperationFailedException {
        final String connectionName = context.getCurrentAddressValue();
        final String outboundSocketBindingRef = LocalOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF.resolveModelAttribute(context, model).asString();
        final ServiceName outboundSocketBindingDependency = context.getCapabilityServiceName(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, outboundSocketBindingRef, OutboundSocketBinding.class);

        final ServiceName serviceName = OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(connectionName);
        final ServiceName aliasServiceName = LocalOutboundConnectionService.LOCAL_OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);
        final ServiceName deprecatedServiceName = AbstractOutboundConnectionService.OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);

        final ServiceBuilder<?> builder = context.getServiceTarget().addService(serviceName);
        final Consumer<LocalOutboundConnectionService> serviceConsumer = builder.provides(deprecatedServiceName, aliasServiceName);
        final Supplier<OutboundSocketBinding> osbSupplier = builder.requires(outboundSocketBindingDependency);
        builder.requires(RemotingServices.SUBSYSTEM_ENDPOINT);
        builder.setInstance(new LocalOutboundConnectionService(serviceConsumer, osbSupplier));
        builder.install();
    }

}
