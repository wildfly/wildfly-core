/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.remoting.AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY;

import java.net.URI;
import java.util.function.Consumer;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundConnection;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.Service;
import org.jboss.remoting3.Endpoint;

/**
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class LocalOutboundConnectionAdd extends AbstractAddStepHandler {

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        installRuntimeService(context, resource.getModel());
    }

    static void installRuntimeService(final OperationContext context, final ModelNode model) {

        final CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addCapability(OUTBOUND_CONNECTION_CAPABILITY);
        final Consumer<OutboundConnection> injector = builder.provides(OUTBOUND_CONNECTION_CAPABILITY);
        builder.requiresCapability(RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY.getName(), Endpoint.class);
        builder.setInstance(Service.newInstance(injector, new InsecureOutboundConnection(URI.create("local:-"))));
        builder.install();
    }
}
