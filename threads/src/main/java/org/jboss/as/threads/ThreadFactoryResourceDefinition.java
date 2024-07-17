/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.util.concurrent.ThreadFactory;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a thread factory resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadFactoryResourceDefinition extends SimpleResourceDefinition {
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
}
