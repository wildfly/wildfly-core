/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.jboss.as.patching.Constants.LAYERS;
import static org.jboss.as.patching.IoUtils.newFile;
import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.runner.TestUtils.tree;

import java.util.List;

import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.runner.AbstractTaskTestCase;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class BaseLayerTestCase extends AbstractTaskTestCase {

    @Test
    public void baseLayerIsAlwaysInstalled() throws Exception {
        tree(env.getInstalledImage().getJbossHome());

        InstalledIdentity installedIdentity = loadInstalledIdentity();
        List<Layer> layers = installedIdentity.getLayers();
        assertEquals(1, layers.size());
        Layer layer = layers.get(0);
        assertEquals(BASE, layer.getName());

        PatchableTarget.TargetInfo targetInfo = layer.loadTargetInfo();
        assertEquals(BASE, targetInfo.getCumulativePatchID());
        assertTrue(targetInfo.getPatchIDs().isEmpty());
        DirectoryStructure directoryStructure = targetInfo.getDirectoryStructure();
        assertEquals(newFile(env.getBundleRepositoryRoot(), "system", LAYERS, BASE), directoryStructure.getBundleRepositoryRoot());
        assertEquals(newFile(env.getModuleRoot(), "system", LAYERS, BASE), directoryStructure.getModuleRoot());
    }
}
