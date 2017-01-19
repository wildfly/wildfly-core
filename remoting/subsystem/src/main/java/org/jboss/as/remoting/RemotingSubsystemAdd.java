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


import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
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

    static final RemotingSubsystemAdd INSTANCE = new RemotingSubsystemAdd();

    static final OperationContext.AttachmentKey<Boolean> RUNTIME_KEY = OperationContext.AttachmentKey.create(Boolean.class);

    private RemotingSubsystemAdd() {
        super(RemotingSubsystemRootResource.ATTRIBUTES);
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.populateModel(context, operation, resource);
        // Add a step to validate worker-thread-pool vs endpoint and to set up a default endpoint resource if needed
        context.addStep(WorkerThreadPoolVsEndpointHandler.INSTANCE, OperationContext.Stage.MODEL);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        // Signal RemotingEndpointAdd that we ran
        context.attach(RUNTIME_KEY, Boolean.FALSE);

        launchServices(context);
    }

    void launchServices(final OperationContext context) throws OperationFailedException {

        ModelNode endpointModel = context.readResource(PathAddress.pathAddress(RemotingEndpointResource.ENDPOINT_PATH)).getModel();
        String workerName = RemotingEndpointResource.WORKER.resolveModelAttribute(context, endpointModel).asString();

        final OptionMap map = EndpointConfigFactory.populate(context, endpointModel);

        // create endpoint
        final String nodeName = WildFlySecurityManager.getPropertyPrivileged(RemotingExtension.NODE_NAME_PROPERTY, null);
        final EndpointService endpointService = new EndpointService(nodeName, EndpointService.EndpointType.SUBSYSTEM, map);

        // In case of a managed server the subsystem endpoint might already be installed {@see DomainServerCommunicationServices}
        if (context.getProcessType() == ProcessType.DOMAIN_SERVER) {
            final ServiceController<?> controller = context.getServiceRegistry(false).getService(RemotingServices.SUBSYSTEM_ENDPOINT);
            if (controller != null) {
                // if installed, just skip the rest
                return;
            }
        }

        context.getServiceTarget().addCapability(RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY, endpointService)
                .addCapabilityRequirement(RemotingSubsystemRootResource.IO_WORKER_CAPABILITY, workerName, XnioWorker.class, endpointService.getWorker())
                .install();
    }

    private boolean areWorkerAttributesSet(final OperationContext context, final ModelNode model) throws OperationFailedException {
        for (final AttributeDefinition attribute : RemotingSubsystemRootResource.ATTRIBUTES) {
            ModelNode value = attribute.resolveModelAttribute(context,model);
            if (value.isDefined() && !value.equals(attribute.getDefaultValue())) {
                return true;
            }
        }
        return false;
    }
}
