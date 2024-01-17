/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
