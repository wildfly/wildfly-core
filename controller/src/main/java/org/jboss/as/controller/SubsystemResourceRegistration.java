/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.version.Stability;

/**
 * Describe the persistent aspects of a subsystem resource.
 * @author Paul Ferraro
 */
public interface SubsystemResourceRegistration extends ResourceRegistration {

    String getName();

    @Override
    default PathElement getPathElement() {
        return PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, this.getName());
    }

    static SubsystemResourceRegistration of(String name) {
        return of(name, Stability.DEFAULT);
    }

    static SubsystemResourceRegistration of(String name, Stability stability) {
        return new SubsystemResourceRegistration() {
            @Override
            public String getName() {
                return name;
            }
        };
    }
}
