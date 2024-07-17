/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import static org.jboss.as.threads.CommonAttributes.ENHANCED_QUEUE_THREAD_POOL;

import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.msc.service.ServiceName;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for an {@code org.jboss.threads.EnhancedQueueExecutor} resource.
 */
public final class EnhancedQueueExecutorResourceDefinition extends SimpleResourceDefinition {
    private final EnhancedQueueExecutorWriteAttributeHandler writeAttributeHandler;
    private final EnhancedQueueExecutorMetricsHandler metricsHandler;
    private final boolean registerRuntimeOnly;

    public static EnhancedQueueExecutorResourceDefinition create(boolean registerRuntimeOnly) {
        return create(ENHANCED_QUEUE_THREAD_POOL, ThreadsServices.getThreadFactoryResolver(ENHANCED_QUEUE_THREAD_POOL),
                ThreadsServices.EXECUTOR, registerRuntimeOnly);
    }

    public static EnhancedQueueExecutorResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                                 ServiceName serviceNameBase, boolean registerRuntimeOnly) {
        return create(type, threadFactoryResolver, serviceNameBase, registerRuntimeOnly,
                ThreadsServices.createCapability(type, ExecutorService.class),
                false);
    }

    public static EnhancedQueueExecutorResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                                 ServiceName serviceNameBase, boolean registerRuntimeOnly,
                                                                 RuntimeCapability<Void> capability, boolean allowCoreThreadTimeout) {
        return create(PathElement.pathElement(type), threadFactoryResolver, serviceNameBase, registerRuntimeOnly, capability, allowCoreThreadTimeout);
    }

    public static EnhancedQueueExecutorResourceDefinition create(PathElement path, ThreadFactoryResolver threadFactoryResolver,
                                                                 ServiceName serviceNameBase, boolean registerRuntimeOnly,
                                                                 RuntimeCapability<Void> capability, boolean allowCoreThreadTimeout) {
        EnhancedQueueExecutorAdd addHandler = new EnhancedQueueExecutorAdd(threadFactoryResolver, serviceNameBase, capability, allowCoreThreadTimeout);
        return new EnhancedQueueExecutorResourceDefinition(path, addHandler, capability, serviceNameBase, registerRuntimeOnly);
    }

    private EnhancedQueueExecutorResourceDefinition(PathElement path, EnhancedQueueExecutorAdd addHandler,
                                                    RuntimeCapability<Void> capability, ServiceName serviceNameBase,
                                                    boolean registerRuntimeOnly) {
        super(new SimpleResourceDefinition.Parameters(path,
                new ThreadPoolResourceDescriptionResolver(ENHANCED_QUEUE_THREAD_POOL, ThreadsExtension.RESOURCE_NAME,
                        ThreadsExtension.class.getClassLoader()))
                .setAddHandler(addHandler)
                .setRemoveHandler(new EnhancedQueueExecutorRemove(addHandler))
                .setCapabilities(capability));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.writeAttributeHandler = new EnhancedQueueExecutorWriteAttributeHandler(capability, serviceNameBase);
        this.metricsHandler = new EnhancedQueueExecutorMetricsHandler(capability, serviceNameBase);
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        writeAttributeHandler.registerAttributes(resourceRegistration);
        if (registerRuntimeOnly) {
            metricsHandler.registerAttributes(resourceRegistration);
        }
    }
}
