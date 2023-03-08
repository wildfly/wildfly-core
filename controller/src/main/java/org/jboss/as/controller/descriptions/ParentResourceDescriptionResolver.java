/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    default ResourceDescriptionResolver createChildResolver(PathElement path) {
        return this.createChildResolver(List.of(path));
    }

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via keys generated from the specified paths.
     * @param path1 a path element used to generate description keys
     * @param path2 an alternate path element used to generate description keys
     * @return a resolver of resource descriptions
     */
    default ResourceDescriptionResolver createChildResolver(PathElement path1, PathElement path2) {
        return this.createChildResolver(List.of(path1, path2));
    }

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via keys generated from the specified path.
     * @param paths a variable length arrays of path elements from which to generate description keys
     * @return a resolver of resource descriptions
     */
    default ResourceDescriptionResolver createChildResolver(PathElement... paths) {
        return this.createChildResolver(List.of(paths));
    }

    /**
     * Creates a {@link ResourceDescriptionResolver} whose descriptions are located via keys generated from the specified path.
     * @param paths an ordered list of path elements from which to generate description keys
     * @return a resolver of resource descriptions
     */
    ResourceDescriptionResolver createChildResolver(List<PathElement> paths);
}
