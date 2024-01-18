/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.function.Functions;
import org.wildfly.io.IOServiceDescriptor;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Configures the services of the IO subsystem root resource.
 * @author Paul Ferraro
 */
class IOSubsystemServiceConfigurator implements ResourceServiceConfigurator {

    private final AtomicInteger maxThreads;

    IOSubsystemServiceConfigurator(AtomicInteger maxThreads) {
        this.maxThreads = maxThreads;
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode workers = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS)).get(WorkerResourceDefinition.PATH.getKey());
        WorkerAdd.checkWorkerConfiguration(context, workers);

        ResourceServiceInstaller maxThreadsInstaller = CapabilityServiceInstaller.builder(IOSubsystemRegistrar.MAX_THREADS_CAPABILITY, AtomicInteger::intValue, Functions.constantSupplier(this.maxThreads)).build();

        String defaultWorker = IOSubsystemRegistrar.DEFAULT_WORKER.resolveModelAttribute(context, model).asStringOrNull();
        ResourceServiceInstaller defaultWorkerInstaller = (defaultWorker != null) ? CapabilityServiceInstaller.builder(IOSubsystemRegistrar.DEFAULT_WORKER_CAPABILITY, ServiceDependency.on(IOServiceDescriptor.WORKER, defaultWorker)).build() : null;

        return (defaultWorkerInstaller != null) ? ResourceServiceInstaller.combine(maxThreadsInstaller, defaultWorkerInstaller) : maxThreadsInstaller;
    }
}
