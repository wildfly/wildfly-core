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

    GenericOutboundConnectionAdd() {
        super(GenericOutboundConnectionResourceDefinition.ATTRIBUTES);
    }

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
