/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.Collection;
import java.util.stream.Stream;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.version.Stability;

/**
 * Describe the persistent aspects of a subsystem resource.
 * @author Paul Ferraro
 */
public interface SubsystemResourceDescription extends ResourceDescription {

    String getName();

    @Override
    default PathElement getPathElement() {
        return PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, this.getName());
    }

    /**
     * Creates a basic description of a subsystem resource.
     * @param registration a resource registration
     * @param attributes the attributes of this resource
     * @return a subsystem resource description
     */
    static SubsystemResourceDescription of(ResourceRegistration registration, Collection<AttributeDefinition> attributes) {
        return new SubsystemResourceDescription() {
            @Override
            public String getName() {
                return this.getPathElement().getValue();
            }

            @Override
            public PathElement getPathElement() {
                return registration.getPathElement();
            }

            @Override
            public Stability getStability() {
                return registration.getStability();
            }

            @Override
            public Stream<AttributeDefinition> getAttributes() {
                return attributes.stream();
            }
        };
    }

    /**
     * Creates a basic description of a subsystem resource.
     * @param name the subsystem name
     * @param attributes the attributes of this resource
     * @return a subsystem resource description
     */
    static SubsystemResourceDescription of(String name, Collection<AttributeDefinition> attributes) {
        return new SubsystemResourceDescription() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Stream<AttributeDefinition> getAttributes() {
                return attributes.stream();
            }
        };
    }
}
