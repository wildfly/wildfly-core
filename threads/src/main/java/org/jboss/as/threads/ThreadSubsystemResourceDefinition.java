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
