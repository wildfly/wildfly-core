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

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.as.controller.PathElement;

/**
 * Generates resource descriptions for a given subsystem and exposes a mechanism for generating a {@link ResourceDescriptionResolver} for child resources.
 * @author Paul Ferraro
 */
public class SubsystemResourceDescriptionResolver extends StandardResourceDescriptionResolver implements ParentResourceDescriptionResolver {

    private static final String DEFAULT_RESOURCE_NAME = "LocalDescriptions";

    private final String bundleName;
    private final WeakReference<ClassLoader> bundleLoader;

    /**
     * Constructs a resolver of resource descriptions for the specified subsystem and extension class using the default resource name.
     * @param subsystemName a subsystem name
     * @param targetClass the extension or resource definition class used to locate properties containing resource descriptions
     */
    public SubsystemResourceDescriptionResolver(String subsystemName, Class<?> targetClass) {
        this(subsystemName, targetClass, DEFAULT_RESOURCE_NAME);
    }

    /**
     * Constructs a resolver of resource descriptions for the specified subsystem and extension class using the specified resource name.
     * @param subsystemName a subsystem name
     * @param targetClass the the extension or resource definition class used to locate properties containing resource descriptions
     */
    public SubsystemResourceDescriptionResolver(String subsystemName, Class<?> targetClass, String resourceName) {
        this(subsystemName, String.join(".", targetClass.getPackage().getName(), resourceName), targetClass.getClassLoader());
    }

    private SubsystemResourceDescriptionResolver(String prefix, String bundleName, ClassLoader bundleLoader) {
        super(prefix, bundleName, bundleLoader, true, false);
        this.bundleName = bundleName;
        this.bundleLoader = new WeakReference<>(bundleLoader);
    }

    @Override
    public ParentResourceDescriptionResolver createChildResolver(PathElement path, List<PathElement> alternatePaths) {
        List<ResourceDescriptionResolver> alternates = alternatePaths.isEmpty() ? List.of() : alternatePaths.stream().map(this::createResolver).collect(Collectors.toUnmodifiableList());
        return new ChildResourceDescriptionResolver(this, this.createResolver(path), alternates);
    }

    private ParentResourceDescriptionResolver createResolver(PathElement path) {
        List<CharSequence> keys = path.isWildcard() ? List.of(this.getKeyPrefix(), path.getKey()) : List.of(this.getKeyPrefix(), path.getKey(), path.getValue());
        return new SubsystemResourceDescriptionResolver(String.join(".", keys), this.bundleName, this.bundleLoader.get());
    }
}
