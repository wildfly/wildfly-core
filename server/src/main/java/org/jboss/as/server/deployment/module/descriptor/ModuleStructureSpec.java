/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final List<ModuleDependency> moduleDependencies = new ArrayList<>();
    private final List<DependencySpec> systemDependencies = new ArrayList<>();
    private final List<ResourceRoot> resourceRoots = new ArrayList<>();
    private final List<FilterSpecification> exportFilters = new ArrayList<>();
    private final List<ModuleIdentifier> exclusions = new ArrayList<>();
    private final List<String> classTransformers = new ArrayList<>();
    private final List<String> aliases = new ArrayList<>();
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

    /**
     * Adds an alias to the module structure spec.
     *
     * @param dependency the alias identifier
     * @deprecated use {@link #addAlias(String)} instead
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public void addAlias(final ModuleIdentifier dependency) {
        aliases.add(dependency.toString());
    }

    /**
     * Adds an alias to the module structure spec.
     *
     * @param dependency the alias identifier
     */
    public void addAlias(final String dependency) {
        aliases.add(dependency);
    }

    /**
     * Returns the aliases of this module spec.
     * @return an unmodifiable list with the aliases of this module spec
     * @deprecated use {@link #getAliasesList()} instead
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public List<ModuleIdentifier> getAliases() {
        return aliases.stream().map(ModuleIdentifier::fromString).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the aliases of this module spec.
     * @return an unmodifiable list with the aliases of this module spec
     */
    public List<String> getAliasesList() {
        return Collections.unmodifiableList(aliases);
    }

    public void addAnnotationModule(final String dependency) {
        annotationModules.add(dependency);
    }

    public List<String> getAnnotationModules() {
        return Collections.unmodifiableList(annotationModules);
    }

    /**
     * Returns list of exclusions.
     * @return the exclusions
     * @deprecated use {@link #getExclusionsList()} instead
     */
    @Deprecated(forRemoval = true, since = "28.0.0")
    public List<ModuleIdentifier> getExclusions() {
        return exclusions;
    }

    /**
     * Returns the exclusions of this module spec. Use {@link #addExclusion(String)} to add new exclusions.
     * @return an unmodifiable list with the exclusions of this module spec.
     */
    public List<String> getExclusionsList() {
        return exclusions.stream().map(ModuleIdentifier::toString).collect(Collectors.toUnmodifiableList());
    }

    public void addExclusion(String identifier) {
        this.exclusions.add(ModuleIdentifier.fromString(identifier));
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
