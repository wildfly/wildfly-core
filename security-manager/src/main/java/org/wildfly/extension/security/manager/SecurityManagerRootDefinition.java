/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;

/**
 * Defines the security manager subsystem root resource.
 *
 * @author <a href="sguilhen@jboss.com">Stefan Guilhen</a>
 */
class SecurityManagerRootDefinition extends PersistentResourceDefinition {

    static final SecurityManagerRootDefinition INSTANCE = new SecurityManagerRootDefinition();

    private static final List<? extends PersistentResourceDefinition> CHILDREN = Collections.unmodifiableList(
            Arrays.asList(DeploymentPermissionsResourceDefinition.INSTANCE));

    private SecurityManagerRootDefinition() {
        super (SecurityManagerExtension.SUBSYSTEM_PATH, SecurityManagerExtension.getResolver(),
                SecurityManagerSubsystemAdd.INSTANCE, ReloadRequiredRemoveStepHandler.INSTANCE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptySet();
    }

    @Override
    public List<? extends PersistentResourceDefinition> getChildren() {
        return CHILDREN;
    }
}
