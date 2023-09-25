/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;


import static org.jboss.as.remoting.Capabilities.IO_WORKER_CAPABILITY_NAME;
import static org.jboss.as.remoting.RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY;
import static org.jboss.as.remoting.RemotingSubsystemRootResource.WORKER;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

/**
 * Add operation handler for the remoting subsystem.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Emanuel Muckenhuber
 */
class RemotingSubsystemAdd extends AbstractAddStepHandler {

    private final boolean forDomain;

    RemotingSubsystemAdd(RemotingSubsystemRootResource.Attributes attributes) {
        super(attributes.all);
        this.forDomain = attributes.forDomain;
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.populateModel(context, operation, resource);

        // Add a step to set up a placeholder endpoint resource if needed
        context.addStep(new WorkerThreadPoolVsEndpointHandler(forDomain), OperationContext.Stage.MODEL);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

        // WFCORE-4510 -- the effective endpoint configuration is from the root subsystem resource,
        // not from the placeholder configuration=endpoint child resource.
        ModelNode endpointModel = resource.getModel();
        String workerName = WORKER.resolveModelAttribute(context, endpointModel).asString();

        final OptionMap map = EndpointConfigFactory.populate(context, endpointModel);

        // create endpoint
        final String nodeName = WildFlySecurityManager.getPropertyPrivileged(RemotingExtension.NODE_NAME_PROPERTY, null);

        // In case of a managed server the subsystem endpoint might already be installed {@see DomainServerCommunicationServices}
        if (context.getProcessType() == ProcessType.DOMAIN_SERVER) {
            final ServiceController<?> controller = context.getServiceRegistry(false).getService(RemotingServices.SUBSYSTEM_ENDPOINT);
            if (controller != null) {
                // if installed, just skip the rest
                return;
            }
        }

        final CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addCapability(REMOTING_ENDPOINT_CAPABILITY);
        final Consumer<Endpoint> endpointConsumer = builder.provides(REMOTING_ENDPOINT_CAPABILITY);
        final Supplier<XnioWorker> workerSupplier = builder.requiresCapability(IO_WORKER_CAPABILITY_NAME, XnioWorker.class, workerName);
        builder.setInstance(new EndpointService(endpointConsumer, workerSupplier, nodeName, EndpointService.EndpointType.SUBSYSTEM, map));
        builder.install();
    }
}
