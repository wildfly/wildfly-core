/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstalledImage;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.MiscContentItem;
import org.jboss.as.patching.metadata.ModuleItem;
import org.jboss.as.patching.metadata.Patch;

/**
 * @author Emanuel Muckenhuber
 */
class IdentityPatchContext {


    private final File miscTargetRoot;

    private final PatchEntry identityEntry;
    private final Map<String, PatchContentLoader> contentLoaders = new HashMap<>();

    // TODO initialize layers in the correct order
    private final State state = State.NEW;
    private final Map<String, FailedFileRenaming> renames = new LinkedHashMap<>();

    enum State {

        NEW,
        PREPARED,
        COMPLETED,
        INVALIDATE,
        ROLLBACK_ONLY,

    }

    IdentityPatchContext(final InstallationManager.InstallationModification modification,
                         final InstalledImage installedImage) {

        this.miscTargetRoot = installedImage.getJbossHome();

        this.identityEntry = new IdentityEntry(modification);
    }

    /**
     * Get the patch entry for the identity.
     *
     * @return the identity entry
     */
    PatchEntry getIdentityEntry() {
        return identityEntry;
    }

    protected void failedToRenameFile(final File file, final File target) {
        if (!renames.containsKey(file.getAbsolutePath())) {
            renames.put(file.getAbsolutePath(), new FailedFileRenaming(file, target, getIdentityEntry().applyPatchId));
            PatchLogger.ROOT_LOGGER.cannotRenameFile(file.getAbsolutePath());
        }
    }

    /**
     * Add a rollback loader for a give patch.
     *
     * @param patchId the patch id.
     * @param target  the patchable target
     */
    private void recordRollbackLoader(final String patchId, PatchableTarget.TargetInfo target) {
        // set up the content loader paths
        final DirectoryStructure structure = target.getDirectoryStructure();
        final InstalledImage image = structure.getInstalledImage();
        final File historyDir = image.getPatchHistoryDir(patchId);
        final File miscRoot = new File(historyDir, PatchContentLoader.MISC);
        final File modulesRoot = structure.getModulePatchDirectory(patchId);
        final File bundlesRoot = structure.getBundlesPatchDirectory(patchId);
        final PatchContentLoader loader = PatchContentLoader.create(miscRoot, bundlesRoot, modulesRoot);
        //
        recordContentLoader(patchId, loader);
    }

    /**
     * Record a content loader for a given patch id.
     *
     * @param patchID       the patch id
     * @param contentLoader the content loader
     */
    protected void recordContentLoader(final String patchID, final PatchContentLoader contentLoader) {
        if (contentLoaders.containsKey(patchID)) {
            throw new IllegalStateException("Content loader already registered for patch " + patchID); // internal wrong usage, no i18n
        }
        contentLoaders.put(patchID, contentLoader);
    }

    /**
     * Get the target file for misc items.
     *
     * @param item the misc item
     * @return the target location
     */
    public File getTargetFile(final MiscContentItem item) {
        final State state = this.state;
        if (state == State.NEW || state == State.ROLLBACK_ONLY) {
            return getTargetFile(miscTargetRoot, item);
        } else {
            throw new IllegalStateException(); // internal wrong usage, no i18n
        }
    }

    /**
     * Get a misc file.
     *
     * @param root the root
     * @param item the misc content item
     * @return the misc file
     */
    static File getTargetFile(final File root, final MiscContentItem item) {
        return PatchContentLoader.getMiscPath(root, item);
    }

    class IdentityEntry extends PatchEntry {

        IdentityEntry(InstallationManager.MutablePatchingTarget delegate) {
            super(delegate);
        }
    }

    /**
     * Modification information for a patchable target.
     */
    class PatchEntry extends ContentTaskDefinitions implements InstallationManager.MutablePatchingTarget, PatchingTaskContext {

        private String applyPatchId;
        private final InstallationManager.MutablePatchingTarget delegate;
        private final Set<String> rollbacks = new HashSet<>();

        PatchEntry(final InstallationManager.MutablePatchingTarget delegate) {
            assert delegate != null;
            this.delegate = delegate;
        }

        @Override
        public boolean isApplied(String patchId) {
            return delegate.isApplied(patchId);
        }

        @Override
        public void rollback(String patchId) {
            rollbacks.add(patchId);
            // Rollback
            delegate.rollback(patchId);
            // Record rollback loader
            recordRollbackLoader(patchId, delegate);
        }

        @Override
        public void apply(String patchId, Patch.PatchType patchType) {
            delegate.apply(patchId, patchType);
            applyPatchId = patchId;
        }

        @Override
        public String getCumulativePatchID() {
            return delegate.getCumulativePatchID();
        }

        @Override
        public List<String> getPatchIDs() {
            return delegate.getPatchIDs();
        }

        @Override
        public Properties getProperties() {
            return delegate.getProperties();
        }

        @Override
        public DirectoryStructure getDirectoryStructure() {
            return delegate.getDirectoryStructure();
        }

        @Override
        public PatchableTarget.TargetInfo getModifiedState() {
            return delegate.getModifiedState();
        }

        @Override
        public File getTargetFile(ContentItem item) {
            if (item.getContentType() == ContentType.MISC) {
                return IdentityPatchContext.this.getTargetFile((MiscContentItem) item);
            }
            if (applyPatchId == null || state == State.ROLLBACK_ONLY) {
                throw new IllegalStateException("cannot process rollback tasks for modules/bundles"); // internal wrong usage, no i18n
            }
            final File root;
            final DirectoryStructure structure = delegate.getDirectoryStructure();
            if (item.getContentType() == ContentType.BUNDLE) {
                root = structure.getBundlesPatchDirectory(applyPatchId);
            } else {
                root = structure.getModulePatchDirectory(applyPatchId);
            }
            return PatchContentLoader.getModulePath(root, (ModuleItem) item);
        }
    }

}
