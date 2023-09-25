/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModificationCondition;
import org.jboss.as.patching.metadata.ModificationType;
import org.jboss.as.patching.metadata.ModuleItem;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.metadata.PatchElementProvider;
import org.jboss.as.patching.runner.PatchContentLoader;
import org.jboss.as.patching.runner.TestUtils;
import org.junit.Assert;

/**
 * @author Emanuel Muckenhuber
 */
abstract class PatchStepAssertions {

    protected abstract void before(File installation, Patch patch, InstallationManager manager) throws IOException;
    protected abstract void after(File installation, Patch patch, InstallationManager manager) throws IOException;

    protected static final PatchStepAssertions APPLY = new PatchStepAssertions() {
        @Override
        protected void before(final File installation, final Patch patch, final InstallationManager manager) throws IOException {
            assertNotApplied(patch, manager);
        }

        @Override
        protected void after(File installation, Patch patch, InstallationManager manager) throws IOException {
            assertApplied(patch, manager);
        }
    };
    protected static final PatchStepAssertions ROLLBACK = new PatchStepAssertions() {
        @Override
        protected void before(File installation, Patch patch, InstallationManager manager) throws IOException {
            assertApplied(patch, manager);
        }

        @Override
        protected void after(File installation, Patch patch, InstallationManager manager) throws IOException {
            assertNotApplied(patch, manager);
        }
    };

    protected static final PatchStepAssertions NONE = new PatchStepAssertions() {
        @Override
        protected void before(File installation, Patch patch, InstallationManager manager) throws IOException {

        }

        @Override
        protected void after(File installation, Patch patch, InstallationManager manager) throws IOException {

        }
    };

    static void assertApplied(final Patch patch, InstallationManager manager) throws IOException {
        final String patchID = patch.getPatchId();
        InstalledIdentity installedIdentity = null;
        try {
            installedIdentity = manager.getInstalledIdentity(patch.getIdentity().getName(), null);
        } catch (PatchingException e) {
            Assert.fail(e.getLocalizedMessage());
        }
        final PatchableTarget target = installedIdentity.getIdentity();
        final PatchableTarget.TargetInfo identity = target.loadTargetInfo();
        assertIsApplied(patch.getIdentity().getPatchType(), patchID, identity);
        assertExists(identity.getDirectoryStructure().getInstalledImage().getPatchHistoryDir(patchID));
        assertContentItems(patchID, target, patch.getModifications());
        for (final PatchElement element : patch.getElements()) {
            final PatchElementProvider provider = element.getProvider();
            final PatchableTarget targetElement = provider.isAddOn() ? installedIdentity.getAddOn(provider.getName()) : installedIdentity.getLayer(provider.getName());
            assertIsApplied(provider.getPatchType(), element.getId(), targetElement.loadTargetInfo());
            assertContentItems(element.getId(), targetElement, element.getModifications());
        }
    }

    static void assertNotApplied(final Patch patch, InstallationManager manager) throws IOException {
        InstalledIdentity installedIdentity = null;
        try {
            installedIdentity = manager.getInstalledIdentity(patch.getIdentity().getName(), patch.getIdentity().getVersion());
        } catch (PatchingException e) {
            Assert.fail(e.getLocalizedMessage());
        }
        final PatchableTarget.TargetInfo identity = installedIdentity.getIdentity().loadTargetInfo();
        assertNotApplied(patch.getIdentity().getPatchType(), patch.getPatchId(), identity);
        assertDoesNotExists(identity.getDirectoryStructure().getInstalledImage().getPatchHistoryDir(patch.getPatchId()));
        for (final PatchElement element : patch.getElements()) {
            final PatchElementProvider provider = element.getProvider();
            final PatchableTarget target = provider.isAddOn() ? installedIdentity.getAddOn(provider.getName()) : installedIdentity.getLayer(provider.getName());
            Assert.assertNotNull(target);
            assertNotApplied(provider.getPatchType(), element.getId(), target.loadTargetInfo());
        }
    }

    static void assertNotApplied(final Patch.PatchType patchType, final String patchId, final PatchableTarget.TargetInfo targetInfo) {
        if (patchType == Patch.PatchType.CUMULATIVE) {
            Assert.assertNotEquals(patchId, targetInfo.getCumulativePatchID());
        } else {
            Assert.assertFalse(targetInfo.getPatchIDs().contains(patchId));
        }
        final DirectoryStructure structure = targetInfo.getDirectoryStructure();
        assertDoesNotExists(structure.getBundlesPatchDirectory(patchId));
        assertDoesNotExists(structure.getModulePatchDirectory(patchId));
    }

    static void assertIsApplied(final Patch.PatchType patchType, final String patchId, final PatchableTarget.TargetInfo targetInfo) {
        if (patchType == Patch.PatchType.CUMULATIVE) {
            Assert.assertEquals(patchId, targetInfo.getCumulativePatchID());
            Assert.assertTrue(targetInfo.getPatchIDs().isEmpty());
        } else {
            Assert.assertTrue(targetInfo.getPatchIDs().contains(patchId));
        }
        final DirectoryStructure structure = targetInfo.getDirectoryStructure();
        assertExists(structure.getBundlesPatchDirectory(patchId));
        assertExists(structure.getModulePatchDirectory(patchId));
    }

    static void assertContentItems(final String patchID, final PatchableTarget target, final Collection<ContentModification> modifications) throws IOException {
        for (final ContentModification modification : modifications) {
            assertContentModification(patchID, target, modification);
        }
    }

    static void assertExists(final File file) {
        if (file != null) {
            Assert.assertTrue(file.getAbsolutePath(), file.exists());
        }
    }

    static void assertDoesNotExists(final File file) {
        if (file != null) {
            Assert.assertFalse(file.getAbsolutePath(), file.exists());
        }
    }

    static void assertContentModification(final String patchID, final PatchableTarget target, final ContentModification modification) {
        final ContentItem item = modification.getItem();
        final ContentType contentType = item.getContentType();
        switch (contentType) {
            case MODULE:
                assertModule(patchID, target, (ModuleItem) item);
                break;
            case BUNDLE:
                break;
            case MISC:
                final File home = target.getDirectoryStructure().getInstalledImage().getJbossHome();
                final ModificationCondition condition = modification.getCondition();
                if(condition != null) {
                    if(condition instanceof ModificationCondition.ExistsCondition) {
                        final ContentItem requiredItem = ((ModificationCondition.ExistsCondition)condition).getContentItem();
                        File requiredFile;
                        switch(requiredItem.getContentType()) {
                            case MISC:
                                requiredFile = PatchContentLoader.getMiscPath(home, (MiscContentItem)requiredItem);
                                break;
                            case MODULE:
                            case BUNDLE:
                            default:
                                throw new IllegalStateException("Unsupported content type");
                        }
                        if(!requiredFile.exists()) {
                            final File file = PatchContentLoader.getMiscPath(home, (MiscContentItem)item);
                            Assert.assertFalse(file.exists());
                            return;
                        }
                    }
                }
                assertMisc(home, modification.getType(), (MiscContentItem) item);
                break;
            default:
                Assert.fail();
        }
    }

    static void assertMisc(final File root, final ModificationType modification, final MiscContentItem item) {
        final File file = PatchContentLoader.getMiscPath(root, item);
        Assert.assertTrue(item.getRelativePath(), file.exists() == (modification != ModificationType.REMOVE));
    }

    static void assertModule(final String patchID, final PatchableTarget target, final String moduleName, final String slot) {
        assertModule(patchID, target, new ModuleItem(moduleName, slot, IoUtils.NO_CONTENT));
    }

    static void assertModule(final String patchID, final PatchableTarget target, final ModuleItem item) {
        final File[] mp = TestUtils.getModuleRoot(target);
        final File currentPatch = target.getDirectoryStructure().getModulePatchDirectory(patchID);
        final File module = PatchContentLoader.getModulePath(currentPatch, item);
        assertModulePath(mp, item, module);
    }

    static void assertModulePath(final File[] mp, final ModuleItem item, final File reference) {
        File resolved = null;
        for (final File root : mp) {
            final File moduleRoot = PatchContentLoader.getModulePath(root, item);
            final File moduleXml = new File(moduleRoot, "module.xml");
            if (moduleXml.exists()) {
                resolved = moduleRoot;
                break;
            }
        }
        Assert.assertEquals(item.toString(), reference, resolved);
    }

}
