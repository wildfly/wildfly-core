/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
        UnboundedQueueThreadPoolAdd addHandler = new UnboundedQueueThreadPoolAdd(threadFactoryResolver, serviceNameBase, CAPABILITY);
        return new UnboundedQueueThreadPoolResourceDefinition(path, addHandler, serviceNameBase, registerRuntimeOnly);
    }

    private UnboundedQueueThreadPoolResourceDefinition(PathElement path, UnboundedQueueThreadPoolAdd addHandler,
                                                       ServiceName serviceNameBase, boolean registerRuntimeOnly) {
        super(new SimpleResourceDefinition.Parameters(path,
                new ThreadPoolResourceDescriptionResolver(UNBOUNDED_QUEUE_THREAD_POOL, ThreadsExtension.RESOURCE_NAME,
                        ThreadsExtension.class.getClassLoader()))
                .setAddHandler(addHandler)
                .setRemoveHandler(new UnboundedQueueThreadPoolRemove(addHandler))
                .setCapabilities(CAPABILITY));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.writeAttributeHandler = new UnboundedQueueThreadPoolWriteAttributeHandler(serviceNameBase);
        this.metricsHandler = new UnboundedQueueThreadPoolMetricsHandler(serviceNameBase);
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
