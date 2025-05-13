/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.threads.CommonAttributes.KEEPALIVE_TIME;
import static org.jboss.as.threads.CommonAttributes.TIME;
import static org.jboss.as.threads.CommonAttributes.UNIT;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Utilities related to management of thread pools.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class ThreadPoolManagementUtils {

    static <T> void installThreadPoolService(final Service<T> threadPoolService,
                                             final String threadPoolName,
                                             final RuntimeCapability<Void> cap,
                                             final PathAddress address,
                                             final ServiceName serviceNameBase,
                                             final String threadFactoryName,
                                             final ThreadFactoryResolver threadFactoryResolver,
                                             final Injector<ThreadFactory> threadFactoryInjector,
                                             final String handoffExecutorName,
                                             final HandoffExecutorResolver handoffExecutorResolver,
                                             final Injector<Executor> handoffExecutorInjector,
                                             final ServiceTarget target) {
        final ServiceName threadPoolServiceName;
        final ServiceName aliasServiceName;
        if(cap != null) {
            threadPoolServiceName = cap.getCapabilityServiceName(address);
            if(serviceNameBase != null) {
                aliasServiceName = serviceNameBase.append(threadPoolName);
            } else {
                aliasServiceName = null;
            }
        } else {
            threadPoolServiceName = serviceNameBase.append(threadPoolName);
            aliasServiceName = null;
        }
        final ServiceBuilder<?> serviceBuilder = target.addService(threadPoolServiceName, threadPoolService);
        if(aliasServiceName != null) {
            serviceBuilder.addAliases(aliasServiceName);
        }
        final ServiceName threadFactoryServiceName = threadFactoryResolver.resolveThreadFactory(threadFactoryName,
                threadPoolName, threadPoolServiceName, target);
        serviceBuilder.addDependency(threadFactoryServiceName, ThreadFactory.class, threadFactoryInjector);

        if (handoffExecutorInjector != null) {
            ServiceName handoffServiceName = handoffExecutorResolver.resolveHandoffExecutor(handoffExecutorName,
                    threadPoolName, threadPoolServiceName, target);
            if (handoffServiceName != null) {
                serviceBuilder.addDependency(handoffServiceName, Executor.class, handoffExecutorInjector);
            }
        }

        serviceBuilder.install();

    }

    static void removeThreadPoolService(final String threadPoolName,
            final RuntimeCapability<Void> cap,
            final ServiceName serviceNameBase,
            final String threadFactoryName,
            final ThreadFactoryResolver threadFactoryResolver,
            final OperationContext operationContext) {
        removeThreadPoolService(threadPoolName, cap, serviceNameBase, threadFactoryName, threadFactoryResolver, null, null, operationContext);
    }

    static void removeThreadPoolService(final String threadPoolName,
            final ServiceName serviceNameBase,
            final String threadFactoryName,
            final ThreadFactoryResolver threadFactoryResolver,
            final String handoffExecutorName,
            final HandoffExecutorResolver handoffExecutorResolver,
            final OperationContext operationContext) {
        removeThreadPoolService(threadPoolName, null, serviceNameBase, threadFactoryName, threadFactoryResolver, handoffExecutorName, handoffExecutorResolver, operationContext);
    }

    public static void removeThreadPoolService(String threadPoolName,
            final RuntimeCapability<Void> cap,
            ServiceName serviceNameBase,
            String threadFactoryName,
            ThreadFactoryResolver threadFactoryResolver,
            String handoffExecutorName,
            HandoffExecutorResolver handoffExecutorResolver,
            OperationContext operationContext) {
        final ServiceName threadPoolServiceName;
        if (cap != null) {
            threadPoolServiceName = cap.getCapabilityServiceName(threadPoolName);
        } else {
            threadPoolServiceName = serviceNameBase.append(threadPoolName);
        }
        operationContext.removeService(threadPoolServiceName);
        threadFactoryResolver.releaseThreadFactory(threadFactoryName, threadPoolName, threadPoolServiceName, operationContext);
        if (handoffExecutorResolver != null) {
            handoffExecutorResolver.releaseHandoffExecutor(handoffExecutorName, threadPoolName, threadPoolServiceName, operationContext);
        }
    }

    static BaseThreadPoolParameters parseUnboundedQueueThreadPoolParameters(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        ThreadPoolParametersImpl params = new ThreadPoolParametersImpl();
        return parseBaseThreadPoolOperationParameters(context, operation, model, params);
    }

    static EnhancedQueueThreadPoolParameters parseEnhancedQueueThreadPoolParameters(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        ThreadPoolParametersImpl params = new ThreadPoolParametersImpl();
        parseBaseThreadPoolOperationParameters(context, operation, model, params);

        ModelNode coreTh = PoolAttributeDefinitions.CORE_THREADS.resolveModelAttribute(context, model);
        params.coreThreads = coreTh.isDefined() ? coreTh.asInt() : params.maxThreads;
        return params;
    }

    static BaseThreadPoolParameters parseScheduledThreadPoolParameters(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        ThreadPoolParametersImpl params = new ThreadPoolParametersImpl();
        return parseBaseThreadPoolOperationParameters(context, operation, model, params);
    }

    static EnhancedQueueThreadPoolParameters parseQueuelessThreadPoolParameters(final OperationContext context, final ModelNode operation, final ModelNode model, boolean blocking) throws OperationFailedException {
        ThreadPoolParametersImpl params = new ThreadPoolParametersImpl();
        parseBaseThreadPoolOperationParameters(context, operation, model, params);

        if (!blocking) {
            ModelNode handoffEx = PoolAttributeDefinitions.HANDOFF_EXECUTOR.resolveModelAttribute(context, model);
            params.handoffExecutor = handoffEx.isDefined() ? handoffEx.asString() : null;
        }

        return params;
    }

    static EnhancedQueueThreadPoolParameters parseBoundedThreadPoolParameters(final OperationContext context, final ModelNode operation, final ModelNode model, boolean blocking) throws OperationFailedException {
        ThreadPoolParametersImpl params = new ThreadPoolParametersImpl();
        parseBaseThreadPoolOperationParameters(context, operation, model, params);

        params.allowCoreTimeout = PoolAttributeDefinitions.ALLOW_CORE_TIMEOUT.resolveModelAttribute(context, model).asBoolean();
        if (!blocking) {
            ModelNode handoffEx = PoolAttributeDefinitions.HANDOFF_EXECUTOR.resolveModelAttribute(context, model);
            params.handoffExecutor = handoffEx.isDefined() ? handoffEx.asString() : null;
        }
        ModelNode coreTh = PoolAttributeDefinitions.CORE_THREADS.resolveModelAttribute(context, model);
        params.coreThreads = coreTh.isDefined() ? coreTh.asInt() : params.maxThreads;
        params.queueLength = PoolAttributeDefinitions.QUEUE_LENGTH.resolveModelAttribute(context, model).asInt();
        return params;
    }


    private static ThreadPoolParametersImpl parseBaseThreadPoolOperationParameters(final OperationContext context, final ModelNode operation,
                                                                                   final ModelNode model, final ThreadPoolParametersImpl params) throws OperationFailedException {
        params.address = operation.require(OP_ADDR);
        PathAddress pathAddress = PathAddress.pathAddress(params.address);
        params.name = pathAddress.getLastElement().getValue();

        //Get/validate the properties
        ModelNode tfNode = PoolAttributeDefinitions.THREAD_FACTORY.resolveModelAttribute(context, model);
        params.threadFactory = tfNode.isDefined() ? tfNode.asString() : null;
        params.maxThreads = PoolAttributeDefinitions.MAX_THREADS.resolveModelAttribute(context, model).asInt();

        if (model.hasDefined(KEEPALIVE_TIME)) {
            ModelNode keepaliveTime = model.get(KEEPALIVE_TIME);
            if (!keepaliveTime.hasDefined(TIME)) {
                throw ThreadsLogger.ROOT_LOGGER.missingKeepAliveTime(TIME, KEEPALIVE_TIME);
            }
            if (!keepaliveTime.hasDefined(UNIT)) {
                throw ThreadsLogger.ROOT_LOGGER.missingKeepAliveUnit(UNIT, KEEPALIVE_TIME);
            }
            long time = KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_TIME.resolveModelAttribute(context, keepaliveTime).asLong();
            String unit = KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_UNIT.resolveModelAttribute(context, keepaliveTime).asString();
            params.keepAliveTime = new TimeSpec(Enum.valueOf(TimeUnit.class, unit.toUpperCase(Locale.ENGLISH)), time);
        }

        return params;
    }

    interface BaseThreadPoolParameters {
        ModelNode getAddress();

        String getName();

        String getThreadFactory();

        int getMaxThreads();

        TimeSpec getKeepAliveTime();
    }

    interface EnhancedQueueThreadPoolParameters extends BaseThreadPoolParameters {
        int getCoreThreads();
        boolean isAllowCoreTimeout();
        int getQueueLength();
        String getHandoffExecutor();
    }

    private static class ThreadPoolParametersImpl implements EnhancedQueueThreadPoolParameters {
        ModelNode address;
        String name;
        String threadFactory;
        int maxThreads;
        TimeSpec keepAliveTime;
        String handoffExecutor;
        boolean allowCoreTimeout;
        int coreThreads;
        int queueLength;

        @Override
        public ModelNode getAddress() {
            return address;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getThreadFactory() {
            return threadFactory;
        }

        @Override
        public int getMaxThreads() {
            return maxThreads;
        }

        @Override
        public TimeSpec getKeepAliveTime() {
            return keepAliveTime;
        }

        @Override
        public String getHandoffExecutor() {
            return handoffExecutor;
        }

        @Override
        public boolean isAllowCoreTimeout() {
            return allowCoreTimeout;
        }

        @Override
        public int getCoreThreads() {
            return coreThreads;
        }

        @Override
        public int getQueueLength() {
            return queueLength;
        }
    }

}
