/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import static org.jboss.as.threads.CommonAttributes.UNBOUNDED_QUEUE_THREAD_POOL;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.msc.service.ServiceName;

import java.util.Arrays;
import java.util.Collection;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for an unbounded queue thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class UnboundedQueueThreadPoolResourceDefinition extends PersistentResourceDefinition {
    private final UnboundedQueueThreadPoolWriteAttributeHandler writeAttributeHandler;
    private final UnboundedQueueThreadPoolMetricsHandler metricsHandler;

    private final boolean registerRuntimeOnly;
    public static final RuntimeCapability<Void> CAPABILITY =
            ThreadsServices.createCapability(UNBOUNDED_QUEUE_THREAD_POOL, ManagedJBossThreadPoolExecutorService.class);

    public static UnboundedQueueThreadPoolResourceDefinition create(boolean registerRuntimeOnly) {
        return create(UNBOUNDED_QUEUE_THREAD_POOL, ThreadsServices.getThreadFactoryResolver(UNBOUNDED_QUEUE_THREAD_POOL),
                ThreadsServices.EXECUTOR, registerRuntimeOnly);
    }

    public static UnboundedQueueThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                                    ServiceName serviceNameBase, boolean registerRuntimeOnly) {
        return create(PathElement.pathElement(type), threadFactoryResolver, serviceNameBase, registerRuntimeOnly);
    }

    public static UnboundedQueueThreadPoolResourceDefinition create(PathElement path, ThreadFactoryResolver threadFactoryResolver,
                                                                    ServiceName serviceNameBase, boolean registerRuntimeOnly) {
        return create(path, threadFactoryResolver, serviceNameBase, registerRuntimeOnly, CAPABILITY, false);
    }

    public static UnboundedQueueThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                                    ServiceName serviceNameBase, boolean registerRuntimeOnly,
                                                                    RuntimeCapability<Void> capability, boolean allowCoreThreadTimeout) {
        return create(PathElement.pathElement(type), threadFactoryResolver, serviceNameBase, registerRuntimeOnly, capability, allowCoreThreadTimeout);
    }

    public static UnboundedQueueThreadPoolResourceDefinition create(PathElement path, ThreadFactoryResolver threadFactoryResolver,
                                                                    ServiceName serviceNameBase, boolean registerRuntimeOnly,
                                                                    RuntimeCapability<Void> capability, boolean allowCoreThreadTimeout) {
        UnboundedQueueThreadPoolAdd addHandler = new UnboundedQueueThreadPoolAdd(threadFactoryResolver, serviceNameBase, capability, allowCoreThreadTimeout);
        return new UnboundedQueueThreadPoolResourceDefinition(path, addHandler, capability, serviceNameBase, registerRuntimeOnly);
    }

    private UnboundedQueueThreadPoolResourceDefinition(PathElement path, UnboundedQueueThreadPoolAdd addHandler,
                                                       RuntimeCapability<Void> capability, ServiceName serviceNameBase,
                                                       boolean registerRuntimeOnly) {
        super(new SimpleResourceDefinition.Parameters(path,
                new ThreadPoolResourceDescriptionResolver(UNBOUNDED_QUEUE_THREAD_POOL, ThreadsExtension.RESOURCE_NAME,
                        ThreadsExtension.class.getClassLoader()))
                .setAddHandler(addHandler)
                .setRemoveHandler(new UnboundedQueueThreadPoolRemove(addHandler))
                .setCapabilities(capability));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.writeAttributeHandler = new UnboundedQueueThreadPoolWriteAttributeHandler(capability, serviceNameBase);
        this.metricsHandler = new UnboundedQueueThreadPoolMetricsHandler(capability, serviceNameBase);
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        writeAttributeHandler.registerAttributes(resourceRegistration);
        if (registerRuntimeOnly) {
            metricsHandler.registerAttributes(resourceRegistration);
        }
    }


    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(writeAttributeHandler.attributes);
    }
}
