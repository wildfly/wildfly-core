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

import static org.jboss.as.threads.CommonAttributes.SCHEDULED_THREAD_POOL;

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
 * {@link org.jboss.as.controller.ResourceDefinition} for a scheduled thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ScheduledThreadPoolResourceDefinition extends PersistentResourceDefinition {
    private final ScheduledThreadPoolWriteAttributeHandler writeAttributeHandler;
    private final ScheduledThreadPoolMetricsHandler metricsHandler;
    private final boolean registerRuntimeOnly;
    public static final RuntimeCapability<Void> CAPABILITY = ThreadsServices.createCapability(SCHEDULED_THREAD_POOL, ManagedScheduledExecutorService.class);

    public static ScheduledThreadPoolResourceDefinition create(boolean registerRuntimeOnly) {
        return create(SCHEDULED_THREAD_POOL, ThreadsServices.getThreadFactoryResolver(SCHEDULED_THREAD_POOL), ThreadsServices.EXECUTOR, registerRuntimeOnly);
    }

    public static ScheduledThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                               ServiceName serviceNameBase, boolean registerRuntimeOnly) {
        return create(type, threadFactoryResolver, serviceNameBase, registerRuntimeOnly, CAPABILITY);
    }

    public static ScheduledThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                               ServiceName serviceNameBase, boolean registerRuntimeOnly,
                                                               RuntimeCapability<Void> capability) {
        return create(PathElement.pathElement(type), threadFactoryResolver, serviceNameBase, registerRuntimeOnly, capability);
    }

    public static ScheduledThreadPoolResourceDefinition create(PathElement path, ThreadFactoryResolver threadFactoryResolver,
                                                               ServiceName serviceNameBase, boolean registerRuntimeOnly,
                                                               RuntimeCapability<Void> capability) {
        ScheduledThreadPoolAdd addHandler = new ScheduledThreadPoolAdd(threadFactoryResolver, serviceNameBase, capability);
        return new ScheduledThreadPoolResourceDefinition(path, addHandler, capability, serviceNameBase, registerRuntimeOnly);
    }

    private ScheduledThreadPoolResourceDefinition(PathElement path, ScheduledThreadPoolAdd addHandler,
                                                  RuntimeCapability<Void> capability, ServiceName serviceNameBase,
                                                  boolean registerRuntimeOnly) {
        super(new SimpleResourceDefinition.Parameters(path,
                new ThreadPoolResourceDescriptionResolver(SCHEDULED_THREAD_POOL, ThreadsExtension.RESOURCE_NAME,
                        ThreadsExtension.class.getClassLoader()))
                .setAddHandler(addHandler)
                .setRemoveHandler(new ScheduledThreadPoolRemove(addHandler))
                .setCapabilities(capability));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.writeAttributeHandler = new ScheduledThreadPoolWriteAttributeHandler(capability, serviceNameBase);
        this.metricsHandler = new ScheduledThreadPoolMetricsHandler(capability, serviceNameBase);
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
