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
class MemoryPoolRootResourceDefinition extends SimpleResourceDefinition {
    static final MemoryPoolRootResourceDefinition INSTANCE = new MemoryPoolRootResourceDefinition();
    private MemoryPoolRootResourceDefinition() {
        super(new Parameters(PlatformMBeanConstants.MEMORY_POOL_PATH,
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.MEMORY_POOL)).setRuntime());
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(MemoryPoolResourceDefinition.INSTANCE);
    }
}

