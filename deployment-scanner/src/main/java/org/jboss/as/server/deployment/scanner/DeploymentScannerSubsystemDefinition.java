/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * @author Tomaz Cerar
 * @created 25.1.12 17:44
 */
public class DeploymentScannerSubsystemDefinition extends SimpleResourceDefinition {

    protected DeploymentScannerSubsystemDefinition() {
        super(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, DeploymentScannerExtension.SUBSYSTEM_NAME),
                DeploymentScannerExtension.getResourceDescriptionResolver("deployment.scanner.subsystem"),
                DeploymentScannerSubsystemAdd.INSTANCE, ModelOnlyRemoveStepHandler.INSTANCE
        );
    }
}
