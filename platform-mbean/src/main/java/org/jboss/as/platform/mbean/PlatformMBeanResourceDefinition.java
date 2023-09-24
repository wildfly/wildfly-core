/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;

import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
public class PlatformMBeanResourceDefinition extends SimpleResourceDefinition {
    static final PlatformMBeanResourceDefinition INSTANCE = new PlatformMBeanResourceDefinition();

    private PlatformMBeanResourceDefinition() {
        super(new Parameters(PlatformMBeanConstants.PLATFORM_MBEAN_PATH,
                PlatformMBeanUtil.getResolver("platform-mbeans")).setRuntime());
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(ClassLoadingResourceDefinition.INSTANCE);
        if (ManagementFactory.getCompilationMXBean() != null) {
            resourceRegistration.registerSubModel(CompilationResourceDefinition.INSTANCE);
        }
        resourceRegistration.registerSubModel(GarbageCollectorRootResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(MemoryManagerRootResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(MemoryResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(MemoryPoolRootResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(OperatingSystemResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(RuntimeResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(ThreadResourceDefinition.INSTANCE);

        resourceRegistration.registerSubModel(BufferPoolRootResourceDefinition.INSTANCE);
    }
}
