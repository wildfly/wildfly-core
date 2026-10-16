/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import static org.wildfly.extension.security.manager.SecurityManagerExtension.DEPRECATED_SINCE;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.PersistentResourceDefinition;

/**
 * Defines the security manager subsystem root resource.
 *
 * @author <a href="sguilhen@jboss.com">Stefan Guilhen</a>
 */
class SecurityManagerRootDefinition extends PersistentResourceDefinition {

    static final SecurityManagerRootDefinition INSTANCE = new SecurityManagerRootDefinition();

    private static final List<? extends PersistentResourceDefinition> CHILDREN = Collections.unmodifiableList(
            List.of(DeploymentPermissionsResourceDefinition.INSTANCE));

    private SecurityManagerRootDefinition() {
        super (SecurityManagerExtension.SUBSYSTEM_PATH, SecurityManagerExtension.getResolver(),
                ModelOnlyAddStepHandler.INSTANCE, ModelOnlyRemoveStepHandler.INSTANCE);
        setDeprecated(DEPRECATED_SINCE);
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
