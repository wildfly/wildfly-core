/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;


import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the core-management subsystem root resource.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
class CoreManagementRootResourceDefinition extends PersistentResourceDefinition {

    CoreManagementRootResourceDefinition() {
        super(CoreManagementExtension.SUBSYSTEM_PATH,
                CoreManagementExtension.getResourceDescriptionResolver(),
                ModelOnlyAddStepHandler.INSTANCE,
                ReloadRequiredRemoveStepHandler.INSTANCE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptyList();
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Arrays.asList(ConfigurationChangeResourceDefinition.INSTANCE,
                new ProcessStateListenerResourceDefinition()
        );
    }
}
