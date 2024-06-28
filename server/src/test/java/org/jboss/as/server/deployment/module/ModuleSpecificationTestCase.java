/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.jboss.modules.ModuleLoader;
import org.junit.Test;

public class ModuleSpecificationTestCase {

    private static final ModuleLoader TEST_LOADER = new ModuleLoader(ModuleLoader.NO_FINDERS);
    private static final ModuleDependency DEP_A = createModuleDependency("a");
    private static final ModuleDependency DEP_B = createModuleDependency("b");
    private static final ModuleDependency DEP_C = createModuleDependency("c");
    private static final Set<ModuleDependency> ALL_USER_DEPS = Set.of(DEP_A, DEP_B, DEP_C);

    private static ModuleDependency createModuleDependency(String identifier) {
        return new ModuleDependency(TEST_LOADER, identifier, false, false, true, false);
    }

    @Test
    public void testUserDependencyConsistency() {
        ModuleSpecification ms = new ModuleSpecification();
        ms.addUserDependencies(ALL_USER_DEPS);

        // Sanity check
        assertEquals(ALL_USER_DEPS, ms.getUserDependenciesSet());

        // Removal consistency
        ms.removeUserDependencies(md -> md.getIdentifier().getName().equals("a"));
        Set<ModuleDependency> userDepSet = ms.getUserDependenciesSet();
        assertEquals(ALL_USER_DEPS.size() -1, userDepSet.size());
        for (ModuleDependency md : ALL_USER_DEPS) {
            boolean shouldFind = !md.equals(DEP_A);
            assertEquals(shouldFind, userDepSet.contains(md));
        }
    }
}
