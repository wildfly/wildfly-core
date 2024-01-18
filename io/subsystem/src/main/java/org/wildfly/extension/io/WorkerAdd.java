/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_IO_THREADS;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_TASK_CORE_THREADS;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_TASK_MAX_THREADS;
import static org.wildfly.extension.io.WorkerResourceDefinition.STACK_SIZE;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
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
import org.wildfly.io.OptionAttributeDefinition;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class WorkerAdd extends AbstractAddStepHandler {

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
        if (PROFILE.equals(context.getCurrentAddress().getElement(0).getKey())) {
            // Just do the standard thing
            return super.createResource(context);
        }
        Resource r = new WorkerResourceDefinition.WorkerResource(context);
        context.addResource(PathAddress.EMPTY_ADDRESS, r);
        return r;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        Resource resource = context.readResourceFromRoot(address.subAddress(0, address.size() - 1));
        ModelNode workers = Resource.Tools.readModel(resource).get(WorkerResourceDefinition.PATH.getKey());
        int allWorkerCount = workers.asList().size();
        final String name = context.getCurrentAddressValue();
        final XnioWorker.Builder builder = Xnio.getInstance().createWorkerBuilder();
        ModelNode val = STACK_SIZE.resolveModelAttribute(context, model);
        if ((0 < val.asLong()) && (val.asLong() < 100000)) {
            IOLogger.ROOT_LOGGER.wrongStackSize(val.asLong(),name);
        }
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
        ModelNode coreTaskThreadsModel = WORKER_TASK_CORE_THREADS.resolveModelAttribute(context, model);
        ModelNode maxTaskThreadsModel = WORKER_TASK_MAX_THREADS.resolveModelAttribute(context, model);
        int cpuCount = getCpuCount();
        int ioThreadsCalculated = getSuggestedIoThreadCount();
        int workerThreads = builder.getMaxWorkerPoolSize();
        int coreWorkerThreads = coreTaskThreadsModel.asInt();
        if (!ioThreadsModel.isDefined() && !maxTaskThreadsModel.isDefined()) {
            workerThreads = getWorkerThreads(name, allWorkerCount);
            builder.setWorkerIoThreads(ioThreadsCalculated);
            builder.setCoreWorkerPoolSize(coreWorkerThreads);
            builder.setMaxWorkerPoolSize(workerThreads);
            IOLogger.ROOT_LOGGER.printDefaults(name, ioThreadsCalculated, workerThreads, cpuCount);
        } else {
            if (!ioThreadsModel.isDefined()) {
                builder.setWorkerIoThreads(ioThreadsCalculated);
                IOLogger.ROOT_LOGGER.printDefaultsIoThreads(name, ioThreadsCalculated, cpuCount);
            }
            if (!maxTaskThreadsModel.isDefined()) {
                workerThreads = getWorkerThreads(name, allWorkerCount);
                builder.setCoreWorkerPoolSize(coreWorkerThreads);
                builder.setMaxWorkerPoolSize(workerThreads);
                IOLogger.ROOT_LOGGER.printDefaultsWorkerThreads(name, workerThreads, cpuCount);
            }
        }

        registerMax(context, name, workerThreads);

        final CapabilityServiceBuilder<?> capBuilder = context.getCapabilityServiceTarget().addCapability(WorkerResourceDefinition.CAPABILITY);
        final Consumer<XnioWorker> workerConsumer = capBuilder.provides(WorkerResourceDefinition.CAPABILITY);
        final Supplier<ExecutorService> executorSupplier = capBuilder.requiresCapability("org.wildfly.management.executor", ExecutorService.class);
        capBuilder.setInstance(new WorkerService(workerConsumer, executorSupplier, builder));
        capBuilder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        capBuilder.install();
    }

    private void registerMax(OperationContext context, String name, int workerThreads) {
        ServiceName serviceName = IORootDefinition.IO_MAX_THREADS_RUNTIME_CAPABILITY.getCapabilityServiceName();
        MaxThreadTrackerService service = (MaxThreadTrackerService) context.getServiceRegistry(false).getRequiredService(serviceName).getService();
        service.registerWorkerMax(name, workerThreads);
    }
}
