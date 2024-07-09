/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jboss.modules.ModuleLoader;
import org.junit.Test;

public class ModuleDependencyUnitTestCase {

    private static final String MODULE_NAME = "foo";
    private static final ModuleLoader TEST_LOADER = new ModuleLoader(ModuleLoader.NO_FINDERS);

    @Test
    public void testBasicBuilder() {
        ModuleDependency dep = ModuleDependency.Builder.of(TEST_LOADER, MODULE_NAME).build();
        assertEquals(TEST_LOADER, dep.getModuleLoader());
        assertEquals(MODULE_NAME, dep.getIdentifier().getName());
        assertFalse(dep.isExport());
        assertFalse(dep.isImportServices());
        assertFalse(dep.isOptional());
        assertFalse(dep.isUserSpecified());
        assertNotNull(dep.getReason());
        assertTrue(dep.getReason().isEmpty());
    }

    @Test
    public void testSpecifiedBuilder() {

        ModuleDependency dep = ModuleDependency.Builder.of(TEST_LOADER, MODULE_NAME)
                .setExport(true)
                .setImportServices(true)
                .setOptional(true)
                .setUserSpecified(true)
                .setReason(MODULE_NAME)
                .build();

        assertEquals(TEST_LOADER, dep.getModuleLoader());
        assertEquals(MODULE_NAME, dep.getIdentifier().getName());
        assertTrue(dep.isExport());
        assertTrue(dep.isImportServices());
        assertTrue(dep.isOptional());
        assertTrue(dep.isUserSpecified());
        assertFalse(dep.getReason().isEmpty());
        assertEquals(MODULE_NAME, dep.getReason().get());
    }
}
