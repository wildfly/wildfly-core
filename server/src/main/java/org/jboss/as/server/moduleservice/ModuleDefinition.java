/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.moduleservice;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.modules.ModuleSpec;

/**
 *
 * Basically a copy of the same information that is in the module spec, because module spec
 * has no public methods to get anything out of.
 *
 * @author Stuart Douglas
 */
public class ModuleDefinition {

    private final String moduleIdentifier;
    private final Set<ModuleDependency> dependencies;
    private final ModuleSpec moduleSpec;


    public ModuleDefinition(final String moduleIdentifier, final Set<ModuleDependency> dependencies, final ModuleSpec moduleSpec) {
        this.moduleIdentifier = moduleIdentifier;
        this.dependencies = dependencies;
        this.moduleSpec = moduleSpec;
    }

    public String getModuleName() {
        return moduleIdentifier;
    }

    public ModuleSpec getModuleSpec() {
        return moduleSpec;
    }

    public Set<ModuleDependency> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }
}
