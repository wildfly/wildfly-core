/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.OperationEntry;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class IORootDefinition extends PersistentResourceDefinition {
    static final String IO_MAX_THREADS_RUNTIME_CAPABILITY_NAME = "org.wildfly.io.max-threads";

    static final RuntimeCapability<Void> IO_MAX_THREADS_RUNTIME_CAPABILITY =
               RuntimeCapability.Builder.of(IO_MAX_THREADS_RUNTIME_CAPABILITY_NAME, false, Integer.class).build();


    static final IORootDefinition INSTANCE = new IORootDefinition();

    static final PersistentResourceDefinition[] CHILDREN = {
            WorkerResourceDefinition.INSTANCE,
            BufferPoolResourceDefinition.INSTANCE
        };

    private IORootDefinition() {
        super(new SimpleResourceDefinition.Parameters(IOExtension.SUBSYSTEM_PATH, IOExtension.getResolver())
                .setAddHandler(IOSubsystemAdd.INSTANCE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .addCapabilities(IO_MAX_THREADS_RUNTIME_CAPABILITY));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptySet();
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Arrays.asList(CHILDREN);
    }
}
