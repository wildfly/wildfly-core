/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.APPLICATION_CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONSTRAINT;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.management._private.DomainManagementResolver;

/**
 *
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ApplicationClassificationParentResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(CONSTRAINT, APPLICATION_CLASSIFICATION);
    public static final ApplicationClassificationParentResourceDefinition INSTANCE = new ApplicationClassificationParentResourceDefinition();

    private ApplicationClassificationParentResourceDefinition() {
        super(PATH_ELEMENT, DomainManagementResolver
                .getResolver("core.access-control.constraint.application-classification"));
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(ApplicationClassificationTypeResourceDefinition.INSTANCE);
    }

}
