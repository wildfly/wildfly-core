/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class BufferPoolRootResourceDefinition extends SimpleResourceDefinition {
    static final BufferPoolRootResourceDefinition INSTANCE = new BufferPoolRootResourceDefinition();

    private BufferPoolRootResourceDefinition() {
        super(new Parameters(PlatformMBeanConstants.BUFFER_POOL_PATH,
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.BUFFER_POOL)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadOnlyAttribute(PlatformMBeanConstants.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(BufferPoolResourceDefinition.INSTANCE);
    }
}

