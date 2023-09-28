    /*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.remoting.AbstractOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.OutboundConnection;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.Service;
import org.jboss.remoting3.Endpoint;

/**
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class GenericOutboundConnectionAdd extends AbstractAddStepHandler {

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource)
            throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(resource);
        installRuntimeService(context, fullModel);
    }

    static void installRuntimeService(final OperationContext context, final ModelNode fullModel) throws OperationFailedException {

        // Get the destination URI
        final URI uri = getDestinationURI(context, fullModel);

        final CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addCapability(OUTBOUND_CONNECTION_CAPABILITY);
        final Consumer<OutboundConnection> injector = builder.provides(OUTBOUND_CONNECTION_CAPABILITY);
        builder.setInstance(Service.newInstance(injector, new InsecureOutboundConnection(uri)));
        builder.requiresCapability(RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY.getName(), Endpoint.class);
        builder.install();
    }

    static URI getDestinationURI(final OperationContext context, final ModelNode outboundConnection) throws OperationFailedException {
        final String uri = GenericOutboundConnectionResourceDefinition.URI.resolveModelAttribute(context, outboundConnection).asString();
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw RemotingLogger.ROOT_LOGGER.couldNotCreateURI(uri,e.toString());
        }
    }
}
