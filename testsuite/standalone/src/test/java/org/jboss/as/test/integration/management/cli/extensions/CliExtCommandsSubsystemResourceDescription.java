/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.extensions;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;


/**
 *
 * @author Alexey Loubyansky
 */
public class CliExtCommandsSubsystemResourceDescription extends SimpleResourceDefinition {


    public static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, CliExtCommandsExtension.SUBSYSTEM_NAME);

    public CliExtCommandsSubsystemResourceDescription() {
        super(PATH, NonResolvingResourceDescriptionResolver.INSTANCE, new ModelOnlyAddStepHandler(), new AbstractRemoveStepHandler(){});
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
    }
}
