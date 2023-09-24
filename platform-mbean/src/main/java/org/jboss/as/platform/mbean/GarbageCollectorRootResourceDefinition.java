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
class GarbageCollectorRootResourceDefinition extends SimpleResourceDefinition {
    static final GarbageCollectorRootResourceDefinition INSTANCE = new GarbageCollectorRootResourceDefinition();
    private GarbageCollectorRootResourceDefinition() {
        super(new Parameters(PlatformMBeanConstants.GARBAGE_COLLECTOR_PATH,
                PlatformMBeanUtil.getResolver("garbage-collectors")).setRuntime());
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(GarbageCollectorResourceDefinition.INSTANCE);
    }
}

