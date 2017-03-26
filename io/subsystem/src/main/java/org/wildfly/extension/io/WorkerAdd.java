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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.wildfly.extension.io.WorkerResourceDefinition.ATTRIBUTES;
import static org.wildfly.extension.io.WorkerResourceDefinition.IO_WORKER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_IO_THREADS;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_TASK_MAX_THREADS;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.common.cpu.ProcessorInfo;
import org.wildfly.extension.io.logging.IOLogger;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
class WorkerAdd extends AbstractAddStepHandler {
    static final WorkerAdd INSTANCE = new WorkerAdd();

    private WorkerAdd() {
        super(ATTRIBUTES);
    }

    private static int getMaxDescriptorCount() {
        try {
            ObjectName oName = new ObjectName("java.lang:type=OperatingSystem");
            MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
            Object maxResult = conn.getAttribute(oName, "MaxFileDescriptorCount");
            if (maxResult != null) {
                IOLogger.ROOT_LOGGER.tracef("System has MaxFileDescriptorCount set to %d", maxResult);
                return ((Long)maxResult).intValue();
            }
        } catch (Exception e) {
            //noting we can do, some OSs don't support this attribute
        }
        IOLogger.ROOT_LOGGER.tracef("We cannot get MaxFileDescriptorCount from system, not applying any limits");
        return -1;
    }
    private static int getCpuCount(){
        return ProcessorInfo.availableProcessors();
    }

    private static int getMaxPossibleThreadCount(int maxFD) {
        return (maxFD - 600) / 3; //each thread uses two FDs + some overhead;
    }

    private static int getSuggestedTaskCount() {
        return getCpuCount() * 16;
    }

    private static int getSuggestedIoThreadCount() {
        return getCpuCount() * 2;
    }

    private static int getWorkerThreads(String workerName, int totalWorkerCount) {
        int suggestedCount = getSuggestedTaskCount();
        int count = suggestedCount;
        int maxFD = getMaxDescriptorCount();
        if (maxFD > -1) {
            int maxPossible = getMaxPossibleThreadCount(maxFD);
            maxPossible /= totalWorkerCount; //we need to evenly split max threads across all workers
            if (maxPossible < 5) {
                count = 5;
            } else if (maxPossible < suggestedCount) {
                count = maxPossible;
                IOLogger.ROOT_LOGGER.lowFD(workerName, suggestedCount, getCpuCount());
            }
        }
        return count;
    }

    private static int getGlobalSuggestedCount(final OperationContext context, final ModelNode workers) throws OperationFailedException {
        int count = 0;
        if (!workers.isDefined()){
            return count;
        }
        for (Property property : workers.asPropertyList()) {
            ModelNode worker = property.getValue();
            ModelNode ioThreadsModel = WORKER_IO_THREADS.resolveModelAttribute(context, worker);
            ModelNode maxTaskThreadsModel = WORKER_TASK_MAX_THREADS.resolveModelAttribute(context, worker);
            if (ioThreadsModel.isDefined()) {
                count += ioThreadsModel.asInt();
            } else {
                count += getSuggestedIoThreadCount();
            }
            if (maxTaskThreadsModel.isDefined()) {
                count += maxTaskThreadsModel.asInt();
            } else {
                count += getSuggestedTaskCount();
            }
        }
        return count;
    }

    static void checkWorkerConfiguration(final OperationContext context, final ModelNode workers) throws OperationFailedException {
        IOLogger.ROOT_LOGGER.trace("Checking worker configuration");
        int requiredCount = getGlobalSuggestedCount(context, workers);
        IOLogger.ROOT_LOGGER.tracef("Global required thread count is: %d", requiredCount);
        int requiredFDCount = (requiredCount * 3) + 600;
        IOLogger.ROOT_LOGGER.tracef("Global required FD count is: %d", requiredFDCount);
        int maxFd = getMaxDescriptorCount();
        if (maxFd > -1 && maxFd < requiredFDCount) {
            IOLogger.ROOT_LOGGER.lowGlobalFD(maxFd, requiredFDCount);
        }
    }

    @Override
    protected Resource createResource(OperationContext context) {
        Resource r = new WorkerResourceDefinition.WorkerResource(context);
        context.addResource(PathAddress.EMPTY_ADDRESS, r);
        return r;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        Resource resource = context.readResourceFromRoot(address.subAddress(0, address.size() - 1));
        ModelNode workers = Resource.Tools.readModel(resource).get(IOExtension.WORKER_PATH.getKey());
        int allWorkerCount = workers.asList().size();
        final String name = context.getCurrentAddressValue();
        final XnioWorker.Builder builder = Xnio.getInstance().createWorkerBuilder();

        final OptionMap.Builder optionMapBuilder = OptionMap.builder();

        for (OptionAttributeDefinition attr : WorkerResourceDefinition.ATTRIBUTES) {
            Option option = attr.getOption();
            ModelNode value = attr.resolveModelAttribute(context, model);
            if (!value.isDefined()) {
                continue;
            }
            if (attr.getType() == ModelType.INT) {
                optionMapBuilder.set((Option<Integer>) option, value.asInt());
            } else if (attr.getType() == ModelType.LONG) {
                optionMapBuilder.set(option, value.asLong());
            } else if (attr.getType() == ModelType.BOOLEAN) {
                optionMapBuilder.set(option, value.asBoolean());
            }
        }
        builder.populateFromOptions(optionMapBuilder.getMap());
        builder.setWorkerName(name);

        ModelNode ioThreadsModel = WORKER_IO_THREADS.resolveModelAttribute(context, model);
        ModelNode maxTaskThreadsModel = WORKER_TASK_MAX_THREADS.resolveModelAttribute(context, model);
        int cpuCount = getCpuCount();
        int ioThreadsCalculated = getSuggestedIoThreadCount();
        int workerThreads = builder.getMaxWorkerPoolSize();
        if (!ioThreadsModel.isDefined() && !maxTaskThreadsModel.isDefined()) {
            workerThreads = getWorkerThreads(name, allWorkerCount);
            builder.setWorkerIoThreads(ioThreadsCalculated);
            builder.setCoreWorkerPoolSize(workerThreads);
            builder.setMaxWorkerPoolSize(workerThreads);
            IOLogger.ROOT_LOGGER.printDefaults(name, ioThreadsCalculated, workerThreads, cpuCount);
        } else {
            if (!ioThreadsModel.isDefined()) {
                builder.setWorkerIoThreads(ioThreadsCalculated);
                IOLogger.ROOT_LOGGER.printDefaultsIoThreads(name, ioThreadsCalculated, cpuCount);
            }
            if (!maxTaskThreadsModel.isDefined()) {
                workerThreads = getWorkerThreads(name, allWorkerCount);
                builder.setCoreWorkerPoolSize(workerThreads);
                builder.setMaxWorkerPoolSize(workerThreads);
                IOLogger.ROOT_LOGGER.printDefaultsWorkerThreads(name, workerThreads, cpuCount);
            }
        }

        registerMax(context, name, workerThreads);

        final WorkerService workerService = new WorkerService(builder);
        context.getCapabilityServiceTarget().addCapability(IO_WORKER_RUNTIME_CAPABILITY, workerService)
                .setInitialMode(ServiceController.Mode.ON_DEMAND)
                .install();
    }

    private void registerMax(OperationContext context, String name, int workerThreads) {
        ServiceName serviceName = IORootDefinition.IO_MAX_THREADS_RUNTIME_CAPABILITY.getCapabilityServiceName();
        MaxThreadTrackerService service = (MaxThreadTrackerService) context.getServiceRegistry(false).getRequiredService(serviceName).getService();
        service.registerWorkerMax(name, workerThreads);
    }
}
