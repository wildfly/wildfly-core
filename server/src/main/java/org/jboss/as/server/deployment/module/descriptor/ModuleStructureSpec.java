/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.server.deployment.module.FilterSpecification;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;

/**
 * @author Stuart Douglas
 */
class ModuleStructureSpec {

    private String moduleName;
    private final List<ModuleDependency> moduleDependencies = new ArrayList<ModuleDependency>();
    private final List<DependencySpec> systemDependencies = new ArrayList<DependencySpec>();
    private final List<ResourceRoot> resourceRoots = new ArrayList<ResourceRoot>();
    private final List<FilterSpecification> exportFilters = new ArrayList<FilterSpecification>();
    private final List<ModuleIdentifier> exclusions = new ArrayList<ModuleIdentifier>();
    private final List<String> classTransformers = new ArrayList<String>();
    private final List<ModuleIdentifier> aliases = new ArrayList<ModuleIdentifier>();
    private final List<String> annotationModules = new ArrayList<>();

    /**
     * Note that this being null is different to an empty list.
     *
     * Null means unspecified, while empty means specified but empty
     *
     * A sub deployment will inherit this from its parent if it is unspecified, but not if
     * it is empty but specified.
     */
    private Set<String> excludedSubsystems;

    private boolean localLast = false;

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public void addModuleDependency(ModuleDependency dependency) {
        moduleDependencies.add(dependency);
    }

    public List<ModuleDependency> getModuleDependencies() {
        return Collections.unmodifiableList(moduleDependencies);
    }

    public void addResourceRoot(ResourceRoot resourceRoot) {
        resourceRoots.add(resourceRoot);
    }

    public List<ResourceRoot> getResourceRoots() {
        return Collections.unmodifiableList(resourceRoots);
    }

    public void addSystemDependency(final DependencySpec dependency) {
        systemDependencies.add(dependency);
    }

    public List<DependencySpec> getSystemDependencies() {
        return Collections.unmodifiableList(systemDependencies);
    }

    public void addAlias(final ModuleIdentifier dependency) {
        aliases.add(dependency);
    }

    public List<ModuleIdentifier> getAliases() {
        return Collections.unmodifiableList(aliases);
    }

    public void addAnnotationModule(final String dependency) {
        annotationModules.add(dependency);
    }

    public List<String> getAnnotationModules() {
        return Collections.unmodifiableList(annotationModules);
    }

    public List<ModuleIdentifier> getExclusions() {
        return exclusions;
    }

    public List<FilterSpecification> getExportFilters() {
        return exportFilters;
    }

    public List<String> getClassTransformers() {
        return classTransformers;
    }

    public boolean isLocalLast() {
        return localLast;
    }

    public void setLocalLast(final boolean localLast) {
        this.localLast = localLast;
    }

    public Set<String> getExcludedSubsystems() {
        return excludedSubsystems;
    }

    public void setExcludedSubsystems(final Set<String> excludedSubsystems) {
        this.excludedSubsystems = excludedSubsystems;
    }
}
