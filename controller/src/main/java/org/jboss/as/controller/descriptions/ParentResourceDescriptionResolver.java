/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.descriptions;

import java.util.List;

import org.jboss.as.controller.PathElement;

/**
 * A factory for creating resource description resolvers for child resources.
 * @author Paul Ferraro
 */
public interface ParentResourceDescriptionResolver extends ResourceDescriptionResolver {

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via keys generated from the specified path.
     * @param path a path element used to generate description keys
     * @return a resolver of resource descriptions
     */
    default ParentResourceDescriptionResolver createChildResolver(PathElement path) {
        return this.createChildResolver(path, List.of());
    }

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via keys generated from the specified path, or the alternate path if no description was found.
     * @param path a path element used to generate description keys
     * @param alternatePath an alternate path element used to generate description keys
     * @return a resolver of resource descriptions
     */
    default ParentResourceDescriptionResolver createChildResolver(PathElement path, PathElement alternatePath) {
        return this.createChildResolver(path, List.of(alternatePath));
    }

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via keys generated from the specified path, or the alternate paths if no description was found.
     * @param path a path element used to generate description keys
     * @param alternatePath1 an alternate path element used to generate description keys
     * @param alternatePath2 a second alternate path element used to generate description keys
     * @return a resolver of resource descriptions
     */
    default ParentResourceDescriptionResolver createChildResolver(PathElement path, PathElement alternatePath1, PathElement alternatePath2) {
        return this.createChildResolver(path, List.of(alternatePath1, alternatePath2));
    }

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via keys generated from the specified path, or the alternate paths if no description was found.
     * @param path a path element used to generate description keys
     * @param alternatePaths alternate path elements used to generate description keys
     * @return a resolver of resource descriptions
     */
    default ParentResourceDescriptionResolver createChildResolver(PathElement path, PathElement... alternatePaths) {
        return this.createChildResolver(path, List.of(alternatePaths));
    }

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via keys generated from the specified path, or the alternate path if no description was found.
     * @param path a path element used to generate description keys
     * @param alternatePaths alternate path elements used to generate description keys
     * @return a resolver of resource descriptions
     */
    ParentResourceDescriptionResolver createChildResolver(PathElement path, List<PathElement> alternates);

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via the specified key.
     * @param key a child description key
     * @return a resolver of resource descriptions
     */
    default ParentResourceDescriptionResolver createChildResolver(String key) {
        return this.createChildResolver(PathElement.pathElement(key), List.of());
    }
}
