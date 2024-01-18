/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.function.Functions;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class IOSubsystemAdd extends AbstractAddStepHandler {

    private final AtomicInteger maxThreads;

    IOSubsystemAdd(AtomicInteger maxThreads) {
        this.maxThreads = maxThreads;
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        ModelNode workers = Resource.Tools.readModel(resource).get(WorkerResourceDefinition.PATH.getKey());
        WorkerAdd.checkWorkerConfiguration(context, workers);

        CapabilityServiceInstaller.builder(IOSubsystemRegistrar.IO_MAX_THREADS_RUNTIME_CAPABILITY, AtomicInteger::intValue, Functions.constantSupplier(this.maxThreads)).build().install(context);
    }
}
