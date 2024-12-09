/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.Collection;
import java.util.stream.Stream;

import org.jboss.as.version.Stability;

/**
 * Describes the persistent aspects of a resource.
 * @author Paul Ferraro
 */
public interface ResourceDescription extends ResourceRegistration {

    /**
     * Returns a "path key" used to identify the resource during parsing.
     * Normally, this is the same as {@link #getPathElement()}.
     * Mutually exclusive child resources of a given parent resource should share the same path key.
     * @return a path element used to identify the resource during parsing.
     */
    default PathElement getPathKey() {
        return this.getPathElement();
    }

    /**
     * Returns the attributes of this resource.
     * @return a stream of attributes
     */
    default Stream<AttributeDefinition> getAttributes() {
        return Stream.empty();
    }

    /**
     * Creates a basic description of a resource.
     * @param registration a resource registration
     * @param attributes the attributes of this resource
     * @return a resource description
     */
    static ResourceDescription of(ResourceRegistration registration, Collection<AttributeDefinition> attributes) {
        return new ResourceDescription() {
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
     * Creates a basic description of a resource.
     * @param path a resource path element
     * @param attributes the attributes of this resource
     * @return a resource description
     */
    static ResourceDescription of(PathElement path, Collection<AttributeDefinition> attributes) {
        return new ResourceDescription() {
            @Override
            public PathElement getPathElement() {
                return path;
            }

            @Override
            public Stream<AttributeDefinition> getAttributes() {
                return attributes.stream();
            }
        };
    }
}
