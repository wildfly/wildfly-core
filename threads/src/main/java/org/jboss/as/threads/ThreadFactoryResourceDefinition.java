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

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ThreadFactory;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a thread factory resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadFactoryResourceDefinition extends PersistentResourceDefinition {
    public static final ThreadFactoryResourceDefinition DEFAULT_INSTANCE = new ThreadFactoryResourceDefinition();

    public ThreadFactoryResourceDefinition() {
        this(CommonAttributes.THREAD_FACTORY);
    }

    public ThreadFactoryResourceDefinition(String type) {
        this(PathElement.pathElement(type), RuntimeCapability.Builder.of("org.wildfly.threads." + type , true, ThreadFactory.class).build());
    }

    public ThreadFactoryResourceDefinition(PathElement path, RuntimeCapability capability) {
        super(new SimpleResourceDefinition.Parameters(path,
                new StandardResourceDescriptionResolver(CommonAttributes.THREAD_FACTORY, ThreadsExtension.RESOURCE_NAME,
                        ThreadsExtension.class.getClassLoader(), true, false))
                .setAddHandler(new ThreadFactoryAdd(capability))
                .setRemoveHandler(new ServiceRemoveStepHandler(capability.getCapabilityServiceName(), new ThreadFactoryAdd(capability)))
                .setCapabilities(capability));
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        ThreadFactoryWriteAttributeHandler.INSTANCE.registerAttributes(resourceRegistration);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ThreadFactoryWriteAttributeHandler.INSTANCE.attributes);
    }
}
