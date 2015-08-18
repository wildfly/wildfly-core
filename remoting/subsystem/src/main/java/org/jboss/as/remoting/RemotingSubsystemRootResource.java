/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.Endpoint;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class RemotingSubsystemRootResource extends SimpleResourceDefinition {

    //The defaults for these come from XnioWorker
    static final SimpleAttributeDefinition WORKER_READ_THREADS = createIntAttribute(CommonAttributes.WORKER_READ_THREADS, Attribute.WORKER_READ_THREADS, 1);
    static final SimpleAttributeDefinition WORKER_TASK_CORE_THREADS = createIntAttribute(CommonAttributes.WORKER_TASK_CORE_THREADS, Attribute.WORKER_TASK_CORE_THREADS, 4);
    static final SimpleAttributeDefinition WORKER_TASK_KEEPALIVE = createIntAttribute(CommonAttributes.WORKER_TASK_KEEPALIVE, Attribute.WORKER_TASK_KEEPALIVE, 60);
    static final SimpleAttributeDefinition WORKER_TASK_LIMIT = createIntAttribute(CommonAttributes.WORKER_TASK_LIMIT, Attribute.WORKER_TASK_LIMIT, 0x4000);
    static final SimpleAttributeDefinition WORKER_TASK_MAX_THREADS = createIntAttribute(CommonAttributes.WORKER_TASK_MAX_THREADS, Attribute.WORKER_TASK_MAX_THREADS, 16);
    static final SimpleAttributeDefinition WORKER_WRITE_THREADS = createIntAttribute(CommonAttributes.WORKER_WRITE_THREADS, Attribute.WORKER_WRITE_THREADS, 1);

    static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, RemotingExtension.SUBSYSTEM_NAME);

    static AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{
            WORKER_READ_THREADS,
            WORKER_TASK_CORE_THREADS,
            WORKER_TASK_KEEPALIVE,
            WORKER_TASK_LIMIT,
            WORKER_TASK_MAX_THREADS,
            WORKER_WRITE_THREADS
    };

    static final String IO_WORKER_CAPABILITY = "org.wildfly.io.worker";
    static final RuntimeCapability<Void> REMOTING_ENDPOINT_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.remoting.endpoint", Endpoint.class).build();

    public RemotingSubsystemRootResource() {
        super(PATH,
                RemotingExtension.getResourceDescriptionResolver(RemotingExtension.SUBSYSTEM_NAME),
                RemotingSubsystemAdd.INSTANCE,
                new ReloadRequiredRemoveStepHandler(REMOTING_ENDPOINT_CAPABILITY),
                OperationEntry.Flag.RESTART_NONE,
                OperationEntry.Flag.RESTART_ALL_SERVICES);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (final AttributeDefinition attribute : ATTRIBUTES) {
            registerReadWriteIntAttribute(resourceRegistration, attribute);
        }
    }

    private void registerReadWriteIntAttribute(ManagementResourceRegistration resourceRegistration, AttributeDefinition attr) {
        resourceRegistration.registerReadWriteAttribute(attr, null, new ThreadWriteAttributeHandler(attr));
    }

    private static SimpleAttributeDefinition createIntAttribute(String name, Attribute attribute, int defaultValue) {
        return SimpleAttributeDefinitionBuilder.create(name, ModelType.INT, true)
                .setDefaultValue(new ModelNode().set(defaultValue))
                .setXmlName(attribute.getLocalName())
                .setValidator(new IntRangeValidator(1, true))
                .setAllowExpression(true)
                .setDeprecated(ModelVersion.create(2,0))
                .build();
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerCapability(REMOTING_ENDPOINT_CAPABILITY);
    }

    private static class ThreadWriteAttributeHandler extends RestartParentWriteAttributeHandler {

        ThreadWriteAttributeHandler(AttributeDefinition definition) {
            super(CommonAttributes.SUBSYSTEM, definition);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            RemotingSubsystemAdd.INSTANCE.launchServices(context);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return RemotingServices.SUBSYSTEM_ENDPOINT;
        }

        @Override
        protected void validateUpdatedModel(OperationContext context, Resource model) throws OperationFailedException {
            context.addStep(WorkerThreadPoolVsEndpointHandler.INSTANCE, OperationContext.Stage.MODEL);
        }
    }
}
