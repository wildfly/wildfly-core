/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2013, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.wildfly.extension.io;

import static org.wildfly.extension.io.IORootDefinition.IO_MAX_THREADS_RUNTIME_CAPABILITY;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class IOSubsystemAdd extends AbstractAddStepHandler {

    static final IOSubsystemAdd INSTANCE = new IOSubsystemAdd();

    private IOSubsystemAdd() {
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        ModelNode workers = Resource.Tools.readModel(resource).get(IOExtension.WORKER_PATH.getKey());
        WorkerAdd.checkWorkerConfiguration(context, workers);

        MaxThreadTrackerService service = new MaxThreadTrackerService();
        ServiceName serviceName = IO_MAX_THREADS_RUNTIME_CAPABILITY.getCapabilityServiceName();
        ServiceController<Integer> controller = context.getServiceTarget().addService(serviceName, service)
                .setInitialMode(ServiceController.Mode.NEVER)
                .install();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                controller.setMode(ServiceController.Mode.ACTIVE);
                // Rollback handled by the parent step
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
