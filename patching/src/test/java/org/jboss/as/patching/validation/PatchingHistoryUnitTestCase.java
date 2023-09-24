/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import static org.jboss.as.patching.HashUtils.hashFile;
import static org.jboss.as.patching.runner.TestUtils.dump;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.touch;
import static org.jboss.as.patching.validation.PatchHistoryValidations.validateRollbackState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.tests.AbstractPatchingTest;
import org.jboss.as.patching.tests.PatchingTestBuilder;
import org.jboss.as.patching.tests.PatchingTestStepBuilder;
import org.junit.Test;

/**
 * @author Alexey Loubyansky
 */
public class PatchingHistoryUnitTestCase extends AbstractPatchingTest {

    static final String[] FILE_ONE = {"bin", "standalone.sh"};
    static final String[] FILE_TWO = {"bin", "standalone.conf"};
    static final String[] FILE_EXISTING = {"bin", "test"};
    private static final String CP_1_ID = "cp1";
    private static final String ONE_OFF_1_ID = "oneOff1";
    private static final String ONE_OFF_2_ID = "oneOff2";

    @Test
    public void testTreeHandleAll() throws Exception {
        installOneOffCpOneOff();
        final List<String> historyDirs = new ArrayList<String>();
        final List<String> moduleDirs = new ArrayList<String>();
        final List<String> bundleDirs = new ArrayList<String>();

        final PatchHistoryIterator.Builder builder = PatchHistoryIterator.Builder.create(updateInstallationManager().getDefaultIdentity());
        builder.addStateHandler(PatchingArtifacts.HISTORY_DIR, new PatchingArtifactStateHandler<PatchingFileArtifact.DirectoryArtifactState>() {
            @Override
            public void handleValidatedState(PatchingFileArtifact.DirectoryArtifactState state) {
                historyDirs.add(state.getFile().getName());
            }
        });
        builder.addStateHandler(PatchingArtifacts.MODULE_OVERLAY, new PatchingArtifactStateHandler<PatchingFileArtifact.DirectoryArtifactState>() {
            @Override
            public void handleValidatedState(PatchingFileArtifact.DirectoryArtifactState state) {
                moduleDirs.add(state.getFile().getName());
            }
        });
        builder.addStateHandler(PatchingArtifacts.BUNDLE_OVERLAY, new PatchingArtifactStateHandler<PatchingFileArtifact.DirectoryArtifactState>() {
            @Override
            public void handleValidatedState(PatchingFileArtifact.DirectoryArtifactState state) {
                bundleDirs.add(state.getFile().getName());
            }
        });

        final PatchHistoryIterator iterator = builder.iterator();
        while (iterator.hasNext()) {
            iterator.next();
        }

        assertEquals(Arrays.asList(new String[]{"oneOff2", "cp1", "oneOff1"}), historyDirs);
        assertEquals(Arrays.asList(new String[]{"base-oneOff2", "base-cp1", "base-oneOff1"}), moduleDirs);
        assertTrue(bundleDirs.isEmpty());
    }


    @Test
    public void testTreeIterator() throws Exception {

        installOneOffCpOneOff();

        final List<String> historyDirs = new ArrayList<String>();
        final List<String> moduleDirs = new ArrayList<String>();
        final List<String> bundleDirs = new ArrayList<String>();
        final PatchHistoryIterator.Builder builder = PatchHistoryIterator.Builder.create(updateInstallationManager().getDefaultIdentity());
        builder.addStateHandler(PatchingArtifacts.HISTORY_DIR, new PatchingArtifactStateHandler<PatchingFileArtifact.DirectoryArtifactState>() {
            @Override
            public void handleValidatedState(PatchingFileArtifact.DirectoryArtifactState state) {
                historyDirs.add(state.getFile().getName());
            }
        });
        builder.addStateHandler(PatchingArtifacts.MODULE_OVERLAY, new PatchingArtifactStateHandler<PatchingFileArtifact.DirectoryArtifactState>() {
            @Override
            public void handleValidatedState(PatchingFileArtifact.DirectoryArtifactState state) {
                moduleDirs.add(state.getFile().getName());
            }
        });
        builder.addStateHandler(PatchingArtifacts.BUNDLE_OVERLAY, new PatchingArtifactStateHandler<PatchingFileArtifact.DirectoryArtifactState>() {
            @Override
            public void handleValidatedState(PatchingFileArtifact.DirectoryArtifactState state) {
                bundleDirs.add(state.getFile().getName());
            }
        });

        final PatchHistoryIterator tree = builder.iterator();

        assertTrue(tree.hasNext());
        assertTrue(historyDirs.isEmpty());
        assertTrue(moduleDirs.isEmpty());
        assertTrue(bundleDirs.isEmpty());

        assertTrue(tree.hasNext());
        tree.next();
        assertEquals(1, historyDirs.size());
        assertTrue(historyDirs.contains("oneOff2"));
        assertTrue(moduleDirs.contains("base-oneOff2"));
        assertTrue(bundleDirs.isEmpty());

        assertTrue(tree.hasNext());
        tree.next();
        assertEquals(2, historyDirs.size());
        assertTrue(historyDirs.contains("cp1"));
        assertEquals(2, moduleDirs.size());
        assertTrue(moduleDirs.contains("base-cp1"));
        assertTrue(bundleDirs.isEmpty());

        assertTrue(tree.hasNext());
        tree.next();
        assertEquals(3, historyDirs.size());
        assertTrue(historyDirs.contains("oneOff1"));
        assertTrue(moduleDirs.contains("base-oneOff1"));
        assertTrue(bundleDirs.isEmpty());

        assertFalse(tree.hasNext());
    }

    @Test
    public void testSimpleValidRollbackOneOff() throws Exception {
        installOneOff();
        validateRollbackState(ONE_OFF_1_ID, updateInstallationManager().getDefaultIdentity());
    }

    @Test
    public void testSimpleMissingHistory() throws Exception {
        final PatchingTestBuilder builder = installOneOff();
        // Delete complete history
        final File history = getHistory(builder, ONE_OFF_1_ID);
        IoUtils.recursiveDelete(history);
        cannotRollbackPatch(ONE_OFF_1_ID);
    }

    @Test
    public void testSimpleMissingMiscFiles() throws Exception {

        final PatchingTestBuilder builder = installOneOff();
        // Delete misc
        final File history = getHistory(builder, ONE_OFF_1_ID);
        final File misc = new File(history, Constants.MISC);
        IoUtils.recursiveDelete(misc);
        cannotRollbackPatch(ONE_OFF_1_ID);
    }

    @Test
    public void testSimpleMissingRollbackXml() throws Exception {
        final PatchingTestBuilder builder = installOneOff();
        // Delete misc
        final File history = getHistory(builder, ONE_OFF_1_ID);
        final File rollbackXml = new File(history, Constants.ROLLBACK_XML);
        rollbackXml.delete();
        cannotRollbackPatch(ONE_OFF_1_ID);
    }

    @Test
    public void testMissingHistoryOneOff() throws Exception {
        final PatchingTestBuilder builder = installOneOffCpOneOff();

        // Can rollback incl. the first one off
        validateRollbackState(ONE_OFF_1_ID, updateInstallationManager().getDefaultIdentity());

        // Remove one off history
        final File oneOffHistory = getHistory(builder, ONE_OFF_1_ID);
        IoUtils.recursiveDelete(oneOffHistory);
        cannotRollbackPatch(ONE_OFF_1_ID);

        // Can rollback CP1
        validateRollbackState(CP_1_ID, updateInstallationManager().getDefaultIdentity());

        // Remove cp1 history
        final File cpHistory = getHistory(builder, CP_1_ID);
        IoUtils.recursiveDelete(cpHistory);
        cannotRollbackPatch(CP_1_ID);

        // Could still rollback 2nd one off
        validateRollbackState(ONE_OFF_2_ID, updateInstallationManager().getDefaultIdentity());
    }

    @Test
    public void testMissingOverlay() throws Exception {

        final PatchingTestBuilder builder = installOneOffCpOneOff();

        // Can rollback incl. the first one off
        validateRollbackState(ONE_OFF_1_ID, updateInstallationManager().getDefaultIdentity());
        final File overlays = getOverlays(builder, "base", "base-" + ONE_OFF_1_ID);
        IoUtils.recursiveDelete(overlays);

        cannotRollbackPatch(ONE_OFF_1_ID);
        cannotRollbackPatch(CP_1_ID);

    }

    protected void cannotRollbackPatch(final String patchID) throws Exception {
        InstalledIdentity installedIdentity = updateInstallationManager().getDefaultIdentity();
        PatchingException expectedEx = null;
        try {
            validateRollbackState(patchID, installedIdentity);
        } catch (PatchingException e) {
            expectedEx = e;
        }
        assertNotNull("should not be able to rollback " + patchID, expectedEx);
    }

    protected PatchingTestBuilder installOneOffCpOneOff() throws IOException, PatchingException {
        final PatchingTestBuilder builder = createDefaultBuilder();
        final byte[] standaloneHash = new byte[20];
        final byte[] moduleHash = new byte[20];

        // Create a file
        final File existing = builder.getFile(FILE_EXISTING);
        touch(existing);
        dump(existing, randomString());

        final byte[] existingHash = hashFile(existing);
        final byte[] initialHash = Arrays.copyOf(existingHash, existingHash.length);

        final PatchingTestStepBuilder oneOff1 = builder.createStepBuilder();
        oneOff1.setPatchId(ONE_OFF_1_ID)
                .oneOffPatchIdentity(PRODUCT_VERSION)
                .oneOffPatchElement("base-" + ONE_OFF_1_ID, "base", false)
                .addModuleWithRandomContent("org.jboss.test", moduleHash)
                .getParent()
                .addFileWithRandomContent(standaloneHash, FILE_ONE)
                .updateFileWithRandomContent(Arrays.copyOf(existingHash, existingHash.length), existingHash, FILE_EXISTING);

        // Apply oneOff1
        apply(oneOff1);

        final PatchingTestStepBuilder cp1 = builder.createStepBuilder();
        cp1.setPatchId(CP_1_ID)
                .upgradeIdentity(PRODUCT_VERSION, PRODUCT_VERSION)
                .upgradeElement("base-" + CP_1_ID, "base", false)
                .updateModuleWithRandomContent("org.jboss.test", Arrays.copyOf(moduleHash, moduleHash.length), moduleHash)
                .getParent()
                .updateFileWithRandomContent(Arrays.copyOf(standaloneHash, standaloneHash.length), standaloneHash, FILE_ONE)
                .updateFileWithRandomContent(initialHash, existingHash, FILE_EXISTING);

        // Apply CP1
        apply(cp1);

        final PatchingTestStepBuilder oneOff2 = builder.createStepBuilder();
        oneOff2.setPatchId(ONE_OFF_2_ID)
                .oneOffPatchIdentity(PRODUCT_VERSION)
                .oneOffPatchElement("base-" + ONE_OFF_2_ID, "base", false)
                .updateModuleWithRandomContent("org.jboss.test", moduleHash, null)
                .getParent()
                .updateFileWithRandomContent(standaloneHash, null, FILE_ONE)
                .updateFileWithRandomContent(Arrays.copyOf(existingHash, existingHash.length), existingHash, FILE_EXISTING);

        // Apply oneOff1
        apply(oneOff2);
        return builder;
    }


    protected PatchingTestBuilder installOneOff() throws Exception {
        final PatchingTestBuilder builder = createDefaultBuilder();
        final byte[] standaloneHash = new byte[20];
        final byte[] moduleHash = new byte[20];

        // Create a file
        final File existing = builder.getFile(FILE_EXISTING);
        touch(existing);
        dump(existing, randomString());

        final byte[] existingHash = hashFile(existing);
        final byte[] initialHash = Arrays.copyOf(existingHash, existingHash.length);

        final PatchingTestStepBuilder oneOff1 = builder.createStepBuilder();
        oneOff1.setPatchId(ONE_OFF_1_ID)
                .oneOffPatchIdentity(PRODUCT_VERSION)
                .oneOffPatchElement("base-" + ONE_OFF_1_ID, "base", false)
                .addModuleWithRandomContent("org.jboss.test", moduleHash)
                .getParent()
                .addFileWithRandomContent(standaloneHash, FILE_ONE)
                .updateFileWithRandomContent(Arrays.copyOf(existingHash, existingHash.length), existingHash, FILE_EXISTING);

        // Apply oneOff1
        apply(oneOff1);

        return builder;
    }

    static File getHistory(final PatchingTestBuilder builder, final String patchID) {
        return builder.getFile(Constants.INSTALLATION, Constants.PATCHES, patchID);
    }

    static File getOverlays(final PatchingTestBuilder builder, final String layerName, final String patchID) {
        return builder.getFile(Constants.MODULES, Constants.SYSTEM, Constants.LAYERS, layerName, Constants.OVERLAYS, patchID);
    }

}
