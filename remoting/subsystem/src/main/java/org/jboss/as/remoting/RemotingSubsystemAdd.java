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


import static org.jboss.as.remoting.Capabilities.IO_WORKER_CAPABILITY_NAME;
import static org.jboss.as.remoting.RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY;
import static org.jboss.as.remoting.RemotingSubsystemRootResource.WORKER;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
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

    RemotingSubsystemAdd() {
        super(RemotingSubsystemRootResource.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        // DomainServerCommunicationServices will already have created this service if the server group has {@link org.jboss.as.controller.descriptions.ModelDescriptionConstants#MANAGEMENT_SUBSYSTEM_ENDPOINT} enabled.
        if (context.getProcessType().isServer()) {
            ModelNode model = resource.getModel();
            String workerName = WORKER.resolveModelAttribute(context, model).asStringOrNull();

            CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addCapability(REMOTING_ENDPOINT_CAPABILITY);
            Consumer<Endpoint> endpointConsumer = builder.provides(REMOTING_ENDPOINT_CAPABILITY);

            OptionMap map = EndpointConfigFactory.populate(context, model);
            String nodeName = WildFlySecurityManager.getPropertyPrivileged(RemotingExtension.NODE_NAME_PROPERTY, null);

            Supplier<XnioWorker> workerSupplier = builder.requiresCapability(IO_WORKER_CAPABILITY_NAME, XnioWorker.class, workerName);
            builder.setInstance(new EndpointService(endpointConsumer, workerSupplier, nodeName, EndpointService.EndpointType.SUBSYSTEM, map)).install();
        }
    }
}
