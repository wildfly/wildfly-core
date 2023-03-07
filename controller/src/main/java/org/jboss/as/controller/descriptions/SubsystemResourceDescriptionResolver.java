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

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathElement;

/**
 * Generates resource descriptions for a given subsystem and exposes a mechanism for generating a {@link ResourceDescriptionResolver} for child resources.
 * @author Paul Ferraro
 */
public class SubsystemResourceDescriptionResolver extends StandardResourceDescriptionResolver implements ParentResourceDescriptionResolver {

    private static final String DEFAULT_RESOURCE_NAME = "LocalDescriptions";

    /**
     * Constructs a resolver of resource descriptions for the specified subsystem and extension class using the default resource name.
     * @param subsystemName a subsystem name
     * @param extensionClass the extension class used to locate properties containing resource descriptions
     */
    public SubsystemResourceDescriptionResolver(String subsystemName, Class<? extends Extension> extensionClass) {
        this(subsystemName, extensionClass, DEFAULT_RESOURCE_NAME);
    }

    /**
     * Constructs a resolver of resource descriptions for the specified subsystem and extension class using the specified resource name.
     * @param subsystemName a subsystem name
     * @param extensionClass the extension class used to locate properties containing resource descriptions
     */
    public SubsystemResourceDescriptionResolver(String subsystemName, Class<? extends Extension> extensionClass, String resourceName) {
        super(subsystemName, String.join(".", extensionClass.getPackage().getName(), resourceName), extensionClass.getClassLoader(), true, false);
    }

    @Override
    public ResourceDescriptionResolver createChildResolver(List<PathElement> paths) {
        return new ChildResourceDescriptionResolver(this, this.getKeyPrefix(), paths);
    }
}
