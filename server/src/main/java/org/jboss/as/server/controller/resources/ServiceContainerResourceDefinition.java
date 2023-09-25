/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE_CONTAINER;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.operations.DumpServicesHandler;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the service container runtime resources.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */

public class ServiceContainerResourceDefinition extends SimpleResourceDefinition {

    public ServiceContainerResourceDefinition() {
        super(new Parameters(PathElement.pathElement(CORE_SERVICE, SERVICE_CONTAINER),
                ServerDescriptions.getResourceDescriptionResolver("core", SERVICE_CONTAINER))
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SERVICE_CONTAINER));
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(DumpServicesHandler.DEFINITION, DumpServicesHandler.INSTANCE);
    }
}
