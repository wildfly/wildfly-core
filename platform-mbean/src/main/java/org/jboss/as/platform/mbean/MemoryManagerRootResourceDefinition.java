/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class MemoryManagerRootResourceDefinition extends SimpleResourceDefinition {
    static final MemoryManagerRootResourceDefinition INSTANCE = new MemoryManagerRootResourceDefinition();
    private MemoryManagerRootResourceDefinition() {
        super(new Parameters(PlatformMBeanConstants.MEMORY_MANAGER_PATH,
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.MEMORY_MANAGER)).setRuntime());
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(MemoryManagerResourceDefinition.INSTANCE);
    }
}

