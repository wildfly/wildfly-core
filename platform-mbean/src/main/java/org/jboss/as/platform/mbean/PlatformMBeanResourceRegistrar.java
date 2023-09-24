/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Utility for registering platform mbean resources with a parent resource registration (either a server
 * or host level registration.)
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class PlatformMBeanResourceRegistrar {

    public static void registerPlatformMBeanResources(final ManagementResourceRegistration parent) {
        parent.registerSubModel(PlatformMBeanResourceDefinition.INSTANCE);

    }

    private PlatformMBeanResourceRegistrar() {
    }
}
