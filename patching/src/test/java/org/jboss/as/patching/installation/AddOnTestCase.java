/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.jboss.as.patching.Constants.ADD_ONS;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.runner.TestUtils.randomString;

import java.io.File;
import java.util.Collection;

import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.runner.AbstractTaskTestCase;
import org.jboss.as.patching.runner.TestUtils;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class AddOnTestCase extends AbstractTaskTestCase {

    @Test
    public void installedAddOn() throws Exception {
        String addOnName = randomString();
        installAddOn(env.getModuleRoot(), addOnName);

        TestUtils.tree(env.getInstalledImage().getJbossHome());

        InstalledIdentity installedIdentity = loadInstalledIdentity();

        Collection<AddOn> addOns = installedIdentity.getAddOns();
        assertEquals(1, addOns.size());
        AddOn addOn = addOns.iterator().next();
        assertEquals(addOnName, addOn.getName());

        PatchableTarget.TargetInfo targetInfo = addOn.loadTargetInfo();
        assertEquals(BASE, targetInfo.getCumulativePatchID());
        assertTrue(targetInfo.getPatchIDs().isEmpty());
        DirectoryStructure directoryStructure = targetInfo.getDirectoryStructure();
        assertEquals(newFile(env.getModuleRoot(), "system", ADD_ONS, addOnName), directoryStructure.getModuleRoot());
        assertNull(directoryStructure.getBundleRepositoryRoot());
    }

    private static void installAddOn(File baseDir, String... addOns) throws Exception {
        for (String addOn : addOns) {
            IoUtils.mkdir(baseDir, "system", ADD_ONS, addOn);
        }
    }
}
