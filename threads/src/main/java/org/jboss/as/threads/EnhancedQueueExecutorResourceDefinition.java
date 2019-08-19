/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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

package org.jboss.as.threads;

import static org.jboss.as.threads.CommonAttributes.ENHANCED_QUEUE_THREAD_POOL;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.msc.service.ServiceName;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for an {@code org.jboss.threads.EnhancedQueueExecutor} resource.
 */
public final class EnhancedQueueExecutorResourceDefinition extends PersistentResourceDefinition {
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

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(writeAttributeHandler.attributes);
    }
}
