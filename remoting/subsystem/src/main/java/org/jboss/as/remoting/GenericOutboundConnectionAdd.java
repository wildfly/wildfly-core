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
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.wildfly.common.Assert;

/**
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class GenericOutboundConnectionAdd extends AbstractAddStepHandler {

    static final GenericOutboundConnectionAdd INSTANCE = new GenericOutboundConnectionAdd();

    static ModelNode getAddOperation(final String connectionName, final String uri, PathAddress address) {
        Assert.checkNotNullParam("connectionName", connectionName);
        Assert.checkNotEmptyParam("connectionName", connectionName);
        Assert.checkNotNullParam("uri", uri);
        Assert.checkNotEmptyParam("uri", uri);
        final ModelNode addOperation = new ModelNode();
        addOperation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
        addOperation.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());

        // set the other params
        addOperation.get(CommonAttributes.URI).set(uri);

        return addOperation;
    }

    private GenericOutboundConnectionAdd() {
        super(GenericOutboundConnectionResourceDefinition.URI);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource)
            throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(resource);
        installRuntimeService(context, fullModel);
    }

    void installRuntimeService(final OperationContext context, final ModelNode fullModel) throws OperationFailedException {

        final String connectionName = context.getCurrentAddressValue();

        // Get the destination URI
        final URI uri = getDestinationURI(context, fullModel);

        // create the service
        final ServiceName serviceName = OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(connectionName);
        // also add an alias service name to easily distinguish between a generic, remote and local type of connection services
        final ServiceName aliasServiceName = GenericOutboundConnectionService.GENERIC_OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);
        final ServiceName deprecatedServiceName = AbstractOutboundConnectionService.OUTBOUND_CONNECTION_BASE_SERVICE_NAME.append(connectionName);

        final ServiceBuilder<?> builder = context.getServiceTarget().addService(serviceName);
        final Consumer<GenericOutboundConnectionService> serviceConsumer = builder.provides(deprecatedServiceName, aliasServiceName);
        builder.setInstance(new GenericOutboundConnectionService(serviceConsumer, uri));
        builder.requires(RemotingServices.SUBSYSTEM_ENDPOINT);
        builder.install();
    }

    URI getDestinationURI(final OperationContext context, final ModelNode outboundConnection) throws OperationFailedException {
        final String uri = GenericOutboundConnectionResourceDefinition.URI.resolveModelAttribute(context, outboundConnection).asString();
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw RemotingLogger.ROOT_LOGGER.couldNotCreateURI(uri,e.toString());
        }
    }

}
