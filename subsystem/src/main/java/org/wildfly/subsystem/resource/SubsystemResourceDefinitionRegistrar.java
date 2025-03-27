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
     * @deprecated Use {@link SubsystemResourceRegistration} instead.
     */
    @Deprecated(forRemoval = true, since = "28.0")
    static PathElement pathElement(String subsystemName) {
        return PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystemName);
    }
}
