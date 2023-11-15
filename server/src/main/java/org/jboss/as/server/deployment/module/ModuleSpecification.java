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
import java.util.function.Predicate;

import org.jboss.as.server.deployment.SimpleAttachable;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.security.PermissionFactory;

/**
 * Information used to build a module.
 *
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
     * List view of {@link #systemDependenciesSet}.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(forRemoval = true)
    private final List<ModuleDependency> systemDependencies = new ArrayList<>();

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
    private final Set<ModuleDependency> userDependenciesSet = new HashSet<>();

    /**
     * List view of {@link #userDependenciesSet}.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(forRemoval = true)
    private final List<ModuleDependency> userDependencies = new ArrayList<>();

    private final List<ResourceLoaderSpec> resourceLoaders = new ArrayList<>();

    /**
     * The class transformers
     */
    private final List<String> classTransformers = new ArrayList<>();

    private volatile List<ModuleDependency> allDependencies = null;

    /**
     * Modules that cannot be added as dependencies to the deployment, as the user has excluded them
     */
    private final Set<ModuleIdentifier> exclusions = new HashSet<>();

    /**
     * A Map structure that contains an exclusion target module as a key and its aliases as values.
     */
    private final HashMap<ModuleIdentifier, List<ModuleIdentifier>> exclusionsMap = new HashMap<>();

    /**
     * A subset of found dependencies that are excluded in process of deployment, as the user has excluded them
     */
    private final Set<ModuleIdentifier> excludedDependencies = new HashSet<>();

    /**
     * Flag that is set to true if modules of non private sub deployments should be able to see each other
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
     *
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
    private final List<ModuleIdentifier> aliases = new ArrayList<>();

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
        if (!exclusions.contains(dependency.getIdentifier())) {
            if (systemDependenciesSet.add(dependency)) {
                resetDependencyLists(this.systemDependencies);
            }
        } else {
            excludedDependencies.add(dependency.getIdentifier());
        }
    }

    public void addSystemDependencies(final Collection<ModuleDependency> dependencies) {
        for (final ModuleDependency dependency : dependencies) {
            addSystemDependency(dependency);
        }
    }

    public void addUserDependency(final ModuleDependency dependency) {
        if (this.userDependenciesSet.add(dependency)) {
            resetDependencyLists(this.userDependencies);
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
        while (iter.hasNext()) {
            ModuleDependency md = iter.next();
            if (predicate.test(md)) {
                iter.remove();
                resetDependencyLists(userDependencies);
            }
        }
    }

    public void addLocalDependency(final ModuleDependency dependency) {
        if (!exclusions.contains(dependency.getIdentifier())) {
            if (this.localDependenciesSet.add(dependency)) {
                resetDependencyLists(null);
            }
        } else {
            excludedDependencies.add(dependency.getIdentifier());
        }
    }

    public void addLocalDependencies(final Collection<ModuleDependency> dependencies) {
        for (final ModuleDependency dependency : dependencies) {
            addLocalDependency(dependency);
        }
    }

    /** @deprecated use {@link #getSystemDependenciesSet()} */
    @Deprecated(forRemoval = true)
    public List<ModuleDependency> getSystemDependencies() {
        if (systemDependencies.isEmpty()) {
            systemDependencies.addAll(systemDependenciesSet);
        }
        return Collections.unmodifiableList(systemDependencies);
    }

    /**
     * System dependencies are dependencies that are added automatically by the container.
     *
     * @return system dependencies iterable in order of addition. Will not return {@code null}.
     */
    public Set<ModuleDependency> getSystemDependenciesSet() {
        return Collections.unmodifiableSet(systemDependenciesSet);
    }

    public void addExclusion(final ModuleIdentifier exclusion) {
        final String targetModule = ModuleAliasChecker.getTargetModule(exclusion.toString());
        if (targetModule != null) {
            final ModuleIdentifier identifier = ModuleIdentifier.create(targetModule);
            // The exclusion is an alias
            final List<ModuleIdentifier> aliases;
            if (exclusionsMap.containsKey(identifier)) {
                aliases = exclusionsMap.get(identifier);
            } else {
                aliases = new ArrayList<>();
                exclusionsMap.put(identifier, aliases);
            }
            aliases.add(exclusion);
            exclusions.add(identifier);
        } else {
            // The exclusion is not an alias
            exclusionsMap.putIfAbsent(exclusion, new ArrayList<>());
        }
        // list of exclusions, aliases or target modules
        exclusions.add(exclusion);
        Iterator<ModuleDependency> it = systemDependenciesSet.iterator();
        while (it.hasNext()) {
            final ModuleDependency dep = it.next();
            if (dep.getIdentifier().equals(exclusion)) {
                it.remove();
                resetDependencyLists(this.systemDependencies);
            }
        }
        it = localDependenciesSet.iterator();
        while (it.hasNext()) {
            final ModuleDependency dep = it.next();
            if (dep.getIdentifier().equals(exclusion)) {
                it.remove();
                resetDependencyLists(null);
            }
        }
    }

    public void addExclusions(final Iterable<ModuleIdentifier> exclusions) {
        for (final ModuleIdentifier exclusion : exclusions) {
            addExclusion(exclusion);
        }
    }


    /**
     * @deprecated use {@link #getLocalDependenciesSet()} ()}
     */
    @Deprecated(forRemoval = true)
    public List<ModuleDependency> getLocalDependencies() {
        return Collections.unmodifiableList(new ArrayList<>(localDependenciesSet));
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
     * @deprecated use {@link #getUserDependenciesSet()}
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(forRemoval = true)
    public List<ModuleDependency> getUserDependencies() {
        if (userDependencies.isEmpty()) {
            userDependencies.addAll(userDependenciesSet);
        }
        return Collections.unmodifiableList(userDependencies);
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

    /**
     * Gets a modifiable view of the user dependencies list.
     *
     * @return The user dependencies
     *
     * @deprecated use {@link #addUserDependency(ModuleDependency)} and {@link #removeUserDependencies(Predicate)}
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(forRemoval = true)
    public Collection<ModuleDependency> getMutableUserDependencies() {
        resetDependencyLists(this.userDependencies);
        return userDependenciesSet;
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

    @SuppressWarnings("unused")
    public void addAlias(final ModuleIdentifier moduleIdentifier) {
        aliases.add(moduleIdentifier);
    }

    public void addAliases(final Collection<ModuleIdentifier> moduleIdentifiers) {
        aliases.addAll(moduleIdentifiers);
    }

    public List<ModuleIdentifier> getAliases() {
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

    public Set<ModuleIdentifier> getNonexistentExcludedDependencies() {
        // WFCORE-4234 check all excluded dependencies via jboss-deployment-structure.xml are also valid.
        final Set<ModuleIdentifier> unExcludedModuleExclusion = new HashSet<>(exclusions);
        for (ModuleIdentifier identifier : excludedDependencies) {
            for (Map.Entry<ModuleIdentifier, List<ModuleIdentifier>> entry : exclusionsMap.entrySet()) {
                if (entry.getKey().equals(identifier) || entry.getValue().contains(identifier)) {
                    unExcludedModuleExclusion.remove(entry.getKey());
                    unExcludedModuleExclusion.removeAll(entry.getValue());

                    break;
                }
            }
        }
        return unExcludedModuleExclusion;
    }

    private void resetDependencyLists(List<ModuleDependency> listView) {
        if (listView != null) {
            listView.clear();
        }
        allDependencies = null;
    }

}
