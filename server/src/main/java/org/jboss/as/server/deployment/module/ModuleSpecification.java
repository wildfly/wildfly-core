/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.as.server.deployment.SimpleAttachable;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.security.PermissionFactory;

/**
 * Information used to build a module.
 * <p>
 * <strong>This class is not thread safe.</strong> It should only be used by the deployment unit processors
 * associated with a single deployment unit, with a parent deployment and a subdeployment considered to
 * be separate deployments.
 *
 * @author Stuart Douglas
 * @author Marius Bogoevici
 */
public class ModuleSpecification extends SimpleAttachable {

    /**
     * System dependencies are dependencies that are added automatically by the container.
     */
    private final Set<ModuleDependency> systemDependenciesSet = new LinkedHashSet<>();

    /**
     * Local dependencies are dependencies on other parts of the deployment, such as a class-path entry
     */
    private final Set<ModuleDependency> localDependenciesSet = new LinkedHashSet<>();

    /**
     * If set to true this indicates that a dependency on this module requires a dependency on all it's local
     * dependencies.
     */
    private boolean localDependenciesTransitive;

    /**
     * User dependencies are dependencies that the user has specifically added, either via jboss-deployment-structure.xml
     * or via the manifest.
     * <p/>
     * User dependencies are not affected by exclusions.
     */
    private final Set<ModuleDependency> userDependenciesSet = new CopyOnWriteArraySet<>();

    private final List<ResourceLoaderSpec> resourceLoaders = new ArrayList<>();

    /**
     * The class transformers
     */
    private final List<String> classTransformers = new ArrayList<>();

    private volatile List<ModuleDependency> allDependencies = null;

    /**
     * Modules that cannot be added as dependencies to the deployment, as the user has excluded them
     */
    private final Set<String> exclusions = new HashSet<>();

    /**
     * A Map structure that contains an exclusion target module as a key and its aliases as values.
     */
    private final HashMap<String, List<String>> exclusionsMap = new HashMap<>();

    /**
     * A subset of found dependencies that are excluded in process of deployment, as the user has excluded them
     */
    private final Set<String> excludedDependencies = new HashSet<>();

    /**
     * Flag that is set to true if modules of non-private sub deployments should be able to see each other
     */
    private boolean subDeploymentModulesIsolated;

    /**
     * Flag that is set to true if exclusions should be cascaded to sub deployments
     */
    private boolean exclusionsCascadedToSubDeployments;

    /**
     * Flag that indicates that this module should never be visible to other sub deployments
     */
    private boolean privateModule;

    /**
     * Flag that indicates that this module should always be visible to other sub deployments, even if sub deployments
     * are isolated and there is no specify dependency on this module.
     * <p>
     * If sub deployments are not isolated then this flag has no effect.
     *
     */
    private boolean publicModule;

    /**
     * Flag that indicates that local resources should come last in the dependencies list
     */
    private boolean localLast;

    /**
     * Module aliases
     */
    private final Set<String> aliases = new LinkedHashSet<>();

    /**
     * JBoss modules system dependencies, which allow you to specify dependencies on the app class loader
     * to get access to JDK classes.
     */
    private final List<DependencySpec> moduleSystemDependencies = new ArrayList<>();

    /**
     * The minimum permission set for this module, wrapped as {@code PermissionFactory} instances.
     */
    private final List<PermissionFactory> permissionFactories = new ArrayList<>();

    public void addSystemDependency(final ModuleDependency dependency) {
        if (!exclusions.contains(dependency.getIdentifier().toString())) {
            if (systemDependenciesSet.add(dependency)) {
                resetDependencyLists();
            }
        } else {
            excludedDependencies.add(dependency.getIdentifier().toString());
        }
    }

    public void addSystemDependencies(final Collection<ModuleDependency> dependencies) {
        for (final ModuleDependency dependency : dependencies) {
            addSystemDependency(dependency);
        }
    }

    public void addUserDependency(final ModuleDependency dependency) {
        if (this.userDependenciesSet.add(dependency)) {
            resetDependencyLists();
        }
    }

    public void addUserDependencies(final Collection<ModuleDependency> dependencies) {
        for (final ModuleDependency dependency : dependencies) {
            addUserDependency(dependency);
        }
    }

    /**
     * Remove user dependencies that match the predicate.
     *
     * @param predicate test for whether a dependency should be removed. Cannot be {@code null}.
     */
    public void removeUserDependencies(final Predicate<ModuleDependency> predicate) {
        Iterator<ModuleDependency> iter = userDependenciesSet.iterator();
        Set<ModuleDependency> toRemove = null;
        for (ModuleDependency md : userDependenciesSet) {
            if (predicate.test(md)) {
                if (toRemove == null) {
                    toRemove = new HashSet<>();
                }
                toRemove.add(md);
            }
        }
        if (toRemove != null) {
            userDependenciesSet.removeAll(toRemove);
            resetDependencyLists();
        }
    }

    public void addLocalDependency(final ModuleDependency dependency) {
        if (!exclusions.contains(dependency.getIdentifier().toString())) {
            if (this.localDependenciesSet.add(dependency)) {
                resetDependencyLists();
            }
        } else {
            excludedDependencies.add(dependency.getIdentifier().toString());
        }
    }

    public void addLocalDependencies(final Collection<ModuleDependency> dependencies) {
        for (final ModuleDependency dependency : dependencies) {
            addLocalDependency(dependency);
        }
    }

    /**
     * System dependencies are dependencies that are added automatically by the container.
     *
     * @return system dependencies iterable in order of addition. Will not return {@code null}.
     */
    public Set<ModuleDependency> getSystemDependenciesSet() {
        return Collections.unmodifiableSet(systemDependenciesSet);
    }

    /**
     * Records that a module with the given identifier should be excluded from use as a system or local dependency.
     * @param exclusion the module to exclude. Cannot be {@code null}
     *
     * @deprecated use {@link #addModuleExclusion(String)}
     */
    @Deprecated(forRemoval = true)
    public void addExclusion(final ModuleIdentifier exclusion) {
        addModuleExclusion(exclusion.toString());
    }

    /**
     * Records that a module with the given name should be excluded from use as a system or local dependency.
     * @param exclusion the module to exclude. Cannot be {@code null}
     */
    public void addModuleExclusion(final String exclusion) {
        final String targetModule = ModuleAliasChecker.getTargetModule(exclusion);
        if (targetModule != null) {
            // The exclusion is an alias
            List<String> aliases = exclusionsMap.computeIfAbsent(targetModule, k -> new ArrayList<>());
            aliases.add(exclusion);
        } else {
            // The exclusion is not an alias
            exclusionsMap.putIfAbsent(exclusion, new ArrayList<>());
        }
        // list of exclusions, aliases or target modules
        exclusions.add(exclusion);
        Iterator<ModuleDependency> it = systemDependenciesSet.iterator();
        while (it.hasNext()) {
            final ModuleDependency dep = it.next();
            if (dep.getIdentifier().toString().equals(exclusion)) {
                it.remove();
                resetDependencyLists();
            }
        }
        it = localDependenciesSet.iterator();
        while (it.hasNext()) {
            final ModuleDependency dep = it.next();
            if (dep.getIdentifier().toString().equals(exclusion)) {
                it.remove();
                resetDependencyLists();
            }
        }
    }

    /**
     * Records a collection of modules as being {@link #addModuleExclusion(String) excluded}.
     * @param exclusions the identifiers of the modules to exclude. Cannot be {@code null}
     *
     * @deprecated iterate over the exclusions and call {@link #addModuleExclusion(String)}
     */
    @Deprecated(forRemoval = true)
    public void addExclusions(final Iterable<ModuleIdentifier> exclusions) {
        for (final ModuleIdentifier exclusion : exclusions) {
            addExclusion(exclusion);
        }
    }


    /**
     * Local dependencies are dependencies on other parts of the deployment, such as a class-path entry
     *
     * @return local dependencies iterable in order of addition. Will not return {@code null}.
     */
    public Set<ModuleDependency> getLocalDependenciesSet() {
        return Collections.unmodifiableSet(localDependenciesSet);
    }

    /**
     * User dependencies are dependencies that the user has specifically added, either via jboss-deployment-structure.xml
     * or via the manifest.
     * <p/>
     * User dependencies are not affected by exclusions.
     *
     * @return user dependencies iterable in order of addition. Will not return {@code null}.
     */
    public Set<ModuleDependency> getUserDependenciesSet() {
        return Collections.unmodifiableSet(userDependenciesSet);
    }

    @SuppressWarnings("unused")
    public void addResourceLoader(final ResourceLoaderSpec resourceLoader) {
        this.resourceLoaders.add(resourceLoader);
    }

    public List<ResourceLoaderSpec> getResourceLoaders() {
        return Collections.unmodifiableList(resourceLoaders);
    }

    public void addClassTransformer(final String classTransformer) {
        this.classTransformers.add(classTransformer);
    }

    public List<String> getClassTransformers() {
        return Collections.unmodifiableList(classTransformers);
    }

    public boolean isSubDeploymentModulesIsolated() {
        return subDeploymentModulesIsolated;
    }

    public void setSubDeploymentModulesIsolated(final boolean subDeploymentModulesIsolated) {
        this.subDeploymentModulesIsolated = subDeploymentModulesIsolated;
    }

    public boolean isExclusionsCascadedToSubDeployments() {
        return exclusionsCascadedToSubDeployments;
    }

    public void setExclusionsCascadedToSubDeployments(boolean exclusionsCascadedToSubDeployments) {
        this.exclusionsCascadedToSubDeployments = exclusionsCascadedToSubDeployments;
    }

    public boolean isPrivateModule() {
        return privateModule;
    }

    public void setPrivateModule(final boolean privateModule) {
        this.privateModule = privateModule;
    }

    public boolean isPublicModule() {
        return publicModule;
    }

    @SuppressWarnings("unused")
    public void setPublicModule(boolean publicModule) {
        this.publicModule = publicModule;
    }

    /**
     * Returns true if the {@link #getLocalDependenciesSet() local dependencies} added for this {@link ModuleSpecification} should be made
     * transitive (i.e. if any other module 'B' depends on the module 'A' represented by this {@link ModuleSpecification}, then
     * module 'B' will be added with all "local dependencies" that are applicable for module "A"). Else returns false.
     *
     * @return {@code true} if local dependencies should be made transitive
     * @see #getLocalDependenciesSet()
     */
    public boolean isLocalDependenciesTransitive() {
        return localDependenciesTransitive;
    }

    /**
     * Sets whether the {@link #getLocalDependenciesSet() local dependencies} applicable for this {@link ModuleSpecification} are to be treated as transitive dependencies
     * for modules which depend on the module represented by this {@link ModuleSpecification}
     *
     * @param localDependenciesTransitive {@code true} if the {@link #getLocalDependenciesSet()} added for this {@link ModuleSpecification} should be made
     *                                    transitive (i.e. if any other module 'B' depends on the module 'A' represented by
     *                                    this {@link ModuleSpecification}, then module 'B' will be added with
     *                                    all "local dependencies" that are applicable for module "A"). False otherwise
     * @see #getLocalDependenciesSet()
     */
    public void setLocalDependenciesTransitive(final boolean localDependenciesTransitive) {
        this.localDependenciesTransitive = localDependenciesTransitive;
    }

    public boolean isLocalLast() {
        return localLast;
    }

    public void setLocalLast(final boolean localLast) {
        this.localLast = localLast;
    }

    /**
     * Record that another module is an alias for this module.
     * @param moduleIdentifier the identifier of the alias module. Cannot be {@code null}
     *
     * @deprecated use {@link #addModuleAlias(String)}
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("unused")
    public void addAlias(final ModuleIdentifier moduleIdentifier) {
        addModuleAlias(moduleIdentifier.toString());
    }

    /**
     * Record that another module is an alias for this module.
     * @param moduleIdentifier the identifier of the alias module. Cannot be {@code null}
     */
    public void addModuleAlias(final String moduleIdentifier) {
        aliases.add(moduleIdentifier);
    }

    /**
     * Record that a collection of other modules are aliases for this module.
     * @param moduleIdentifiers the identifiers of the alias modules. Cannot be {@code null}
     *
     * @deprecated iterate over the identifiers and call {@link #addModuleAlias(String)}
     */
    @Deprecated(forRemoval = true)
    public void addAliases(final Collection<ModuleIdentifier> moduleIdentifiers) {
        for (ModuleIdentifier id : moduleIdentifiers) {
            addModuleAlias(id.toString());
        }
    }

    /**
     * Gets the identifiers of modules that are aliases for this module.
     * @return the identifiers. Will not return {@code null}
     *
     * @deprecated use {@link #getModuleAliases()}
     */
    @Deprecated(forRemoval = true)
    public List<ModuleIdentifier> getAliases() {
        return aliases.stream().map(ModuleIdentifier::fromString).collect(Collectors.toList());
    }

    /**
     * Gets the names of modules that are aliases for this module.
     * @return the names. Will not return {@code null}
     */
    public Set<String> getModuleAliases() {
        return aliases;
    }

    public List<ModuleDependency> getAllDependencies() {
        if (allDependencies == null) {
            allDependencies = new ArrayList<>();
            allDependencies.addAll(systemDependenciesSet);
            allDependencies.addAll(userDependenciesSet);
            allDependencies.addAll(localDependenciesSet);
        }
        return allDependencies;
    }

    public void addModuleSystemDependencies(final List<DependencySpec> systemDependencies) {
        moduleSystemDependencies.addAll(systemDependencies);
    }

    public List<DependencySpec> getModuleSystemDependencies() {
        return Collections.unmodifiableList(moduleSystemDependencies);
    }

    /**
     * Add a permission factory to this deployment.  This may include permissions not explicitly specified
     * in the domain configuration; such permissions must be validated before being added.
     *
     * @param permissionFactory the permission factory to add
     */
    public void addPermissionFactory(final PermissionFactory permissionFactory) {
        permissionFactories.add(permissionFactory);
    }

    /**
     * Get the permission factory set for this deployment.  This may include permissions not explicitly specified
     * in the domain configuration; such permissions must be validated before being added.
     *
     * @return the permission factory set for this deployment
     */
    public List<PermissionFactory> getPermissionFactories() {
        return permissionFactories;
    }

    /**
     * Gets the identifiers of dependencies that {@link #addModuleExclusion(String) were meant to be excluded} but which were
     * never recorded as a dependency.
     *
     * @return the names. Will not return {@code null}
     *
     * @deprecated use {@link #getFictitiousExcludedDependencies()}
     */
    @Deprecated(forRemoval = true)
    public Set<ModuleIdentifier> getNonexistentExcludedDependencies() {
        return getFictitiousExcludedDependencies().stream().map(ModuleIdentifier::fromString).collect(Collectors.toSet());
    }

    /**
     * Gets the names of dependencies that  {@link #addModuleExclusion(String) were meant to be excluded} but which were
     * never recorded as a dependency.
     *
     * @return the names. Will not return {@code null}
     */
    public Set<String> getFictitiousExcludedDependencies() {
        // WFCORE-4234 check all excluded dependencies via jboss-deployment-structure.xml are also valid.
        final Set<String> unExcludedModuleExclusion = new HashSet<>(exclusions);
        for (String identifier : excludedDependencies) {
            for (Map.Entry<String, List<String>> entry : exclusionsMap.entrySet()) {
                if (entry.getKey().equals(identifier) || entry.getValue().contains(identifier)) {
                    unExcludedModuleExclusion.remove(entry.getKey());
                    entry.getValue().forEach(unExcludedModuleExclusion::remove);
                    break;
                }
            }
        }
        return unExcludedModuleExclusion;
    }

    private void resetDependencyLists() {
        allDependencies = null;
    }

}
