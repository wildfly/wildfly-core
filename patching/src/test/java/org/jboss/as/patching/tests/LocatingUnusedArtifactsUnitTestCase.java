/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.patching.installation.Layer;
import org.jboss.as.patching.validation.PatchingGarbageLocator;
import org.junit.Test;


/**
 * @author Alexey Loubyansky
 *
 */
public class LocatingUnusedArtifactsUnitTestCase extends AbstractPatchingTest {

    static final String[] FILE_ONE = {"bin", "standalone.sh"};
    static final String[] FILE_TWO = {"bin", "standalone.conf"};
    static final String[] FILE_EXISTING = {"bin", "test"};
    private static final String CP_1_ID = "cp1";
    private static final String ONE_OFF_1_ID = "oneOff1";
    private static final String ONE_OFF_2_ID = "oneOff2";

    @Test
    public void testUnpatchedValidation() throws Exception {
        assertNoGarbage();
    }

    @Test
    public void testUnpatchedWitGarbage() throws Exception {

        createDefaultBuilder("layer2", "layer1", "base");

        final List<String> historyGarbage = Arrays.asList(new String[]{getExpectedHistoryDir("CP1"), getExpectedHistoryDir("CP2")});
        for(int i = 0; i < historyGarbage.size(); ++i) {
            new File(historyGarbage.get(i)).mkdirs();
        }

        final List<String> overlayGarbage = Arrays.asList(new String[]{getExpectedOverlayDir("layer2", "CP2"),
                getExpectedOverlayDir("layer1", "CP2"), getExpectedOverlayDir("base", "CP1")});
        for(int i = 0; i < overlayGarbage.size(); ++i) {
            new File(overlayGarbage.get(i)).mkdirs();
        }

        PatchingGarbageLocator garbageLocator = PatchingGarbageLocator.getIninitialized(loadInstallationManager().getDefaultIdentity());
        final List<File> inactiveHistory = garbageLocator.getInactiveHistory();
        assertEqualPaths(historyGarbage, inactiveHistory);

        final List<File> inactiveOverlays = garbageLocator.getInactiveOverlays();
        assertEqualPaths(overlayGarbage, inactiveOverlays);

        garbageLocator.deleteInactiveContent();
        garbageLocator.reset();
        assertTrue(garbageLocator.getInactiveHistory().isEmpty());
        assertTrue(garbageLocator.getInactiveOverlays().isEmpty());
    }

    protected void assertEqualPaths(List<String> expected, final List<File> actual) {
        assertEquals(expected.size(), actual.size());
        for(int i = 0; i < expected.size(); ++i) {
            assertTrue(expected.contains(actual.get(i).getAbsolutePath()));
        }
    }

    protected void removeRollbackXml(String patchId) throws IOException {
        final File oneOff1History = updateInstallationManager().getInstalledImage().getPatchHistoryDir(patchId);
        assertTrue(oneOff1History.exists());
        final File oneOff1RollbackXml = new File(oneOff1History, "rollback.xml");
        assertTrue(oneOff1RollbackXml.exists());
        assertTrue(oneOff1RollbackXml.delete());
        assertFalse(oneOff1RollbackXml.exists());
    }

    protected String getExpectedOverlayDir(String layerName, final String patchId) throws IOException {
        final Layer layer = updateInstallationManager().getDefaultIdentity().getLayer(layerName);
        if(layer == null) {
            fail("No layer " + layerName);
        }
        return layer.getDirectoryStructure().getModulePatchDirectory(layerName + "-" + patchId).getAbsolutePath();
    }

    protected String getExpectedHistoryDir(final String patchId) throws IOException {
        return updateInstallationManager().getInstalledImage().getPatchesDir().getAbsolutePath()
                + File.separator + patchId;
    }



    protected void assertNoGarbage() throws Exception {
        final PatchingGarbageLocator garbageLocator = PatchingGarbageLocator.getIninitialized(loadInstallationManager().getDefaultIdentity());
        List<File> inactiveHistory = garbageLocator.getInactiveHistory();
        assertTrue(inactiveHistory.toString(), inactiveHistory.isEmpty());
        assertTrue(garbageLocator.getInactiveOverlays().toString(), garbageLocator.getInactiveOverlays().isEmpty());
    }

}
