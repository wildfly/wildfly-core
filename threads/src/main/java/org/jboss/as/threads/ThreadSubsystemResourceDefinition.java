/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.descriptions.DeprecatedResourceDescriptionResolver;
import org.jboss.as.controller.registry.OperationEntry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the root resource of the threads subsystem.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@Deprecated
@SuppressWarnings("deprecation")
class ThreadSubsystemResourceDefinition extends PersistentResourceDefinition {
    private final boolean registerRuntimeOnly;

    ThreadSubsystemResourceDefinition(boolean runtimeOnly) {
        //noinspection deprecation
        super(ThreadsExtension.SUBSYSTEM_PATH,
                new DeprecatedResourceDescriptionResolver(ThreadsExtension.SUBSYSTEM_NAME, ThreadsExtension.SUBSYSTEM_NAME, ThreadsExtension.RESOURCE_NAME,
                        ThreadsExtension.class.getClassLoader(), true, false), ThreadsSubsystemAdd.INSTANCE, ReloadRequiredRemoveStepHandler.INSTANCE,
                OperationEntry.Flag.RESTART_NONE, OperationEntry.Flag.RESTART_ALL_SERVICES);
        setDeprecated(ThreadsExtension.DEPRECATED_SINCE);
        this.registerRuntimeOnly = runtimeOnly;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptySet();
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Arrays.asList(
                ThreadFactoryResourceDefinition.DEFAULT_INSTANCE,
                QueuelessThreadPoolResourceDefinition.create(true, registerRuntimeOnly),
                QueuelessThreadPoolResourceDefinition.create(false, registerRuntimeOnly),

                BoundedQueueThreadPoolResourceDefinition.create(true, registerRuntimeOnly),
                BoundedQueueThreadPoolResourceDefinition.create(false, registerRuntimeOnly),

                UnboundedQueueThreadPoolResourceDefinition.create(registerRuntimeOnly),
                ScheduledThreadPoolResourceDefinition.create(registerRuntimeOnly)
        );
    }
}
