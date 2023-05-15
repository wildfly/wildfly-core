/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * Registers the root resource definition of a subsystem.
 * @author Paul Ferraro
 */
public interface SubsystemResourceDefinitionRegistrar extends ResourceDefinitionRegistrar<SubsystemRegistration> {

    /**
     * Returns the {@link PathElement} for the specified subsystem
     * @param subsystemName a subsystem name
     * @return the path element of the specified subsystem
     */
    static PathElement pathElement(String subsystemName) {
        return PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystemName);
    }
}
