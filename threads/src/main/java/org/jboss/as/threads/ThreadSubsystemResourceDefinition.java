/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.DeprecatedResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the root resource of the threads subsystem.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings({"removal", "DeprecatedIsStillUsed"})
class ThreadSubsystemResourceDefinition extends SimpleResourceDefinition {
    private final boolean registerRuntimeOnly;

    ThreadSubsystemResourceDefinition(boolean runtimeOnly) {
        super(new Parameters(ThreadsExtension.SUBSYSTEM_PATH, new DeprecatedResourceDescriptionResolver(ThreadsExtension.SUBSYSTEM_NAME, ThreadsExtension.SUBSYSTEM_NAME, ThreadsExtension.RESOURCE_NAME, ThreadsExtension.class.getClassLoader(), true, false))
                .setAddHandler(ThreadsSubsystemAdd.INSTANCE).setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE).setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES));
        setDeprecated(ThreadsExtension.DEPRECATED_SINCE);
        this.registerRuntimeOnly = runtimeOnly;
    }

    @Override
    public void registerChildren(ManagementResourceRegistration registration) {
        registration.registerSubModel(ThreadFactoryResourceDefinition.DEFAULT_INSTANCE);
        registration.registerSubModel(QueuelessThreadPoolResourceDefinition.create(true, this.registerRuntimeOnly));
        registration.registerSubModel(QueuelessThreadPoolResourceDefinition.create(false, this.registerRuntimeOnly));
        registration.registerSubModel(BoundedQueueThreadPoolResourceDefinition.create(true, this.registerRuntimeOnly));
        registration.registerSubModel(BoundedQueueThreadPoolResourceDefinition.create(false, this.registerRuntimeOnly));
        registration.registerSubModel(UnboundedQueueThreadPoolResourceDefinition.create(this.registerRuntimeOnly));
        registration.registerSubModel(ScheduledThreadPoolResourceDefinition.create(this.registerRuntimeOnly));
    }
}
