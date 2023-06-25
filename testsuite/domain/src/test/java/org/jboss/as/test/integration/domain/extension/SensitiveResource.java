/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.extension;

import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class SensitiveResource extends SimpleResourceDefinition {

    public SensitiveResource(PathElement pathElement) {
        super(new  Parameters(pathElement, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(ModelOnlyAddStepHandler.INSTANCE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN,
                        new ApplicationTypeAccessConstraintDefinition(new ApplicationTypeConfig("security", "security-domain"))));
    }
}
