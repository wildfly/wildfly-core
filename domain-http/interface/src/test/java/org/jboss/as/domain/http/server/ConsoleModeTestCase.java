/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoader;
import org.junit.Test;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ConsoleModeTestCase {


    @Test
    public void testDefaultModules() throws Exception {
        checkModule(null, "org.jboss.as.console", "modules-default");
    }


    @Test
    public void testVersionedNoSlot() throws Exception {
        checkModule(null, ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "1.2.1"), "modules-versioned");
    }

    @Test
    public void testVersionedAndMainSlot() throws Exception {
        checkModule("main", ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "1.2.1"), "modules-versioned");
    }

    @Test
    public void testVersionedLayersNoSlot() throws Exception {
        checkModule(null, ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "1.2.1"), "modules-base-and-layer1");
    }

    @Test
    public void testVersionedLayersAndMainSlot() throws Exception {
        checkModule("main", ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "1.2.1"), "modules-base-and-layer1");
    }

    @Test
    public void testSeveralRootsVersionedLayersNoSlot() throws Exception {
        checkModule(null, ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "3.0.0"), "modules-base-and-layer1", "modules-layer2");
    }

    @Test
    public void testSeveralRootsVersionedLayersAndMainSlot() throws Exception {
        checkModule("main", ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "3.0.0"), "modules-base-and-layer1", "modules-layer2");
    }

    @Test
    public void testSeveralRootsDifferentOrderVersionedLayersNoSlot() throws Exception {
        checkModule(null, ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "3.0.0"), "modules-layer2", "modules-base-and-layer1");
    }

    @Test
    public void testSeveralRootsDifferentOrderVersionedLayersAndMainSlot() throws Exception {
        checkModule("main", ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "3.0.0"), "modules-layer2", "modules-base-and-layer1");
    }

    @Test
    public void testAddonsAndLayersAddon1WinsNoSlot() throws Exception {
        checkModule(null, ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "2.0.0"), "modules-base-and-layer1", "modules-addons1");
    }

    @Test
    public void testAddonsAndLayersLayer2WinsNoSlot() throws Exception {
        checkModule(null, ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "3.0.0"), "modules-base-and-layer1", "modules-layer2", "modules-addons1");
    }

    @Test
    public void testAddonsAndLayersAddon2WinsNoSlot() throws Exception {
        checkModule(null, ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "4.0.0"), "modules-base-and-layer1", "modules-layer2", "modules-addons1", "modules-addons2");
    }

    @Test
    public void testAddonsOnly() throws Exception {
        checkModule(null, ModuleIdentifierUtil.canonicalModuleIdentifier("org.jboss.as.console.main", "4.0.0"), "modules-addons1", "modules-addons2");
    }

    private void checkModule(String slot, String expected, String...moduleDirNames) throws Exception {
        ModuleLoader loader = createModuleLoader(moduleDirNames);
        ClassLoader classLoader = ConsoleMode.ConsoleHandler.findConsoleClassLoader(loader, slot);
        assertNotNull(classLoader);
        assertTrue(classLoader instanceof ModuleClassLoader);
        ModuleClassLoader moduleClassLoader = (ModuleClassLoader)classLoader;
        assertEquals(expected, moduleClassLoader.getModule().getName());
    }

    private ModuleLoader createModuleLoader(String...moduleDirNames) {
        StringBuilder sb = new StringBuilder();
        for (String moduleDirName : moduleDirNames) {
            File file = new File("target/test-classes", moduleDirName);
            assertTrue(file.exists());
            if (sb.length() > 0) {
                sb.append(File.pathSeparatorChar);
            }
            sb.append(file.getAbsolutePath());
        }
        System.setProperty("module.path", sb.toString());
        LocalModuleLoader loader = new LocalModuleLoader();
        return loader;
    }
}
