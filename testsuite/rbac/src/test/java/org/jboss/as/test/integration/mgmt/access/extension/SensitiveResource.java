/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access.extension;


import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.PathElement;
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
        super(new Parameters(pathElement, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(new AbstractAddStepHandler())
                .setRemoveHandler(ModelOnlyRemoveStepHandler.INSTANCE)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN,
                        new ApplicationTypeAccessConstraintDefinition(new ApplicationTypeConfig("rbac", "security-domain"))));
    }
}
