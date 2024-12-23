/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jboss.modules.ModuleLoader;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.junit.Test;

public class ModuleSpecificationTestCase {

    private static final ModuleLoader TEST_LOADER = new ModuleLoader(ModuleLoader.NO_FINDERS);
    private static final ModuleDependency DEP_A = createModuleDependency("a");
    private static final ModuleDependency DEP_B = createModuleDependency("b");
    private static final ModuleDependency DEP_C = createModuleDependency("c");
    private static final Set<ModuleDependency> INITIAL_DEPS = Set.of(DEP_A, DEP_B, DEP_C);

    private static final ModuleDependency DEP_A_FILTERED = createModuleDependency("a", PathFilters.getMetaInfFilter());
    private static final ModuleDependency DEP_B_WITH_REASON = createModuleDependency("a", "because");

    private static final Set<ModuleDependency> ALL_DEPS = Set.of(DEP_A, DEP_B, DEP_C, DEP_A_FILTERED);

    private static final ModuleDependency DEP_D = createModuleDependency("d:aslot");
    private static final ModuleDependency DEP_E = createModuleDependency("e");

    private static ModuleDependency createModuleDependency(String identifier) {
        return createModuleDependency(identifier, (String) null);
    }
    private static ModuleDependency createModuleDependency(String identifier, String reason) {
        return new ModuleDependency(TEST_LOADER, identifier, false, false, true, false, reason);
    }
    private static ModuleDependency createModuleDependency(String identifier, PathFilter importFilter) {
        ModuleDependency dependency = createModuleDependency(identifier, (String) null);
        dependency.addImportFilter(importFilter, true);
        return dependency;
    }

    @Test
    public void testUserDependencyConsistency() {
        ModuleSpecification ms = dependencySetConsistencyTest(
                ModuleSpecification::addUserDependencies,
                ModuleSpecification::getUserDependenciesSet,
                ModuleSpecification::addUserDependency,
                false
        );

        // Removal consistency
        ms.removeUserDependencies(md -> md.getDependencyModule().equals("a"));
        Set<ModuleDependency> userDepSet = ms.getUserDependenciesSet();
        assertEquals(INITIAL_DEPS.size() /* the initials, minus 'a', plus 'd'*/, userDepSet.size());
        for (ModuleDependency md : ALL_DEPS) {
            boolean shouldFind = !md.getDependencyModule().equals("a");
            assertEquals(shouldFind, userDepSet.contains(md));
        }
        assertTrue(userDepSet.contains(DEP_D));
    }

    @Test
    public void testSystemDependencyConsistency() {
        dependencySetConsistencyTest(
                ModuleSpecification::addSystemDependencies,
                ModuleSpecification::getSystemDependenciesSet,
                ModuleSpecification::addSystemDependency,
                true
        );
    }

    @Test
    public void testLocalDependencyConsistency() {
        dependencySetConsistencyTest(
                ModuleSpecification::addLocalDependencies,
                ModuleSpecification::getLocalDependenciesSet,
                ModuleSpecification::addLocalDependency,
                true
        );
    }

    @Test
    public void testModuleAliases() {
        ModuleSpecification ms = new ModuleSpecification();
        for (ModuleDependency dependency : ALL_DEPS) {
            ms.addModuleAlias(dependency.getDependencyModule());
        }
        Set<String> aliases = ms.getModuleAliases();
        assertEquals(ALL_DEPS.size() - 1, aliases.size());
        for (ModuleDependency dep : INITIAL_DEPS) {
            assertTrue(dep + " missing", aliases.contains(dep.getDependencyModule()));
        }
    }

    @Test
    public void testAllDependencies() {
        ModuleSpecification ms = new ModuleSpecification();
        ms.addLocalDependencies(ALL_DEPS);
        ms.addUserDependencies(ALL_DEPS);
        ms.addSystemDependencies(ALL_DEPS);

        List<ModuleDependency> all = ms.getAllDependencies();
        assertEquals(ALL_DEPS.size() * 3, all.size());
        Map<ModuleDependency, Integer> count = new HashMap<>();
        for (ModuleDependency dep : all) {
            Integer val = count.get(dep);
            if (val == null) val = 0;
            count.put(dep, val + 1);
        }
        Integer THREE = 3;
        for (ModuleDependency dep : ALL_DEPS) {
            assertEquals(dep + " has unexpected count in " + all, THREE, count.get(dep));
        }
    }

    private ModuleSpecification dependencySetConsistencyTest(BiConsumer<ModuleSpecification, Collection<ModuleDependency>> setupConsumer,
                                              Function<ModuleSpecification, Set<ModuleDependency>> readFunction,
                                              BiConsumer<ModuleSpecification, ModuleDependency> addConsumer,
                                                             boolean exclusionsSupported) {

        ModuleSpecification ms = new ModuleSpecification();
        setupConsumer.accept(ms, INITIAL_DEPS);

        // Sanity check
        Set<ModuleDependency> depSet = readFunction.apply(ms);
        assertEquals(INITIAL_DEPS, depSet);

        List<ModuleDependency> all = ms.getAllDependencies();
        assertEquals(all.size(), depSet.size());
        for (ModuleDependency dep : all) {
            assertTrue(dep + " is invalid in allDependencies list", depSet.contains(dep));
        }

        try {
            depSet.iterator().remove();
            fail("Should not be able to modify dependency set");
        } catch (UnsupportedOperationException good) {
            // good
        }

        try {
            depSet.add(DEP_A_FILTERED);
            fail("Should not be able to modify dependency set");
        } catch (UnsupportedOperationException good) {
            // good
        }

        // Adding an existing dep with a different reason is ignored
        addConsumer.accept(ms, DEP_B_WITH_REASON);
        depSet = readFunction.apply(ms);
        assertEquals(INITIAL_DEPS, depSet);

        // WFCORE-6442 Adding an existing dep with a different filter is accepted
        addConsumer.accept(ms, DEP_A_FILTERED);
        depSet = readFunction.apply(ms);
        assertEquals(ALL_DEPS, depSet);

        all = ms.getAllDependencies();
        assertEquals(all.size(), depSet.size());
        for (ModuleDependency dep : all) {
            assertTrue(dep + " is invalid in allDependencies list", depSet.contains(dep));
        }

        // Test exclusions are treated as expected
        ms.addModuleExclusion(DEP_D.getDependencyModule());
        addConsumer.accept(ms, DEP_D);
        depSet = readFunction.apply(ms);
        assertEquals(ALL_DEPS.size() + (exclusionsSupported ? 0 : 1), depSet.size());
        assertNotEquals(exclusionsSupported, depSet.contains(DEP_D));
        for (ModuleDependency dep : ALL_DEPS) {
            assertTrue(depSet.contains(dep));
        }

        // Check fictitious exclusion tracking
        ms.addModuleExclusion(DEP_E.getDependencyModule());
        Set<String> fictitious = ms.getFictitiousExcludedDependencies();
        Set<String> expected = exclusionsSupported ? Set.of(DEP_E.getDependencyModule()) : Set.of(DEP_D.getDependencyModule(), DEP_E.getDependencyModule());
        assertEquals(expected, fictitious);

        return ms;
    }
}
