/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.version.Stability;

/**
 * A subsystem resource registration.
 * @author Paul Ferraro
 */
public interface SubsystemResourceRegistration extends ResourceRegistration {

    /**
     * Returns the name of this subsystem.
     * @return the name of this subsystem.
     */
    default String getName() {
        return this.getPathElement().getValue();
    }

    /**
     * Creates a resource registration for the specified subsystem name.
     * @param name a subsystem name
     * @return a resource registration
     */
    static SubsystemResourceRegistration of(String name) {
        return of(name, Stability.DEFAULT);
    }

    /**
     * Creates a new subsystem resource registration for the specified path and stability.
     * @param path a path element
     * @param stability a stability level
     * @return a resource registration
     */
    static SubsystemResourceRegistration of(String name, Stability stability) {
        return new DefaultSubsystemResourceRegistration(name, stability);
    }

    class DefaultSubsystemResourceRegistration extends DefaultResourceRegistration implements SubsystemResourceRegistration {

        DefaultSubsystemResourceRegistration(String name, Stability stability) {
            super(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, name), stability);
        }
    }
}
