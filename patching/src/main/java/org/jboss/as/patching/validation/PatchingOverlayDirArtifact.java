/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import java.io.File;

import org.jboss.as.patching.DirectoryStructure;

/**
 * Artifact representing either a module or bundle overlay directory.
 *
 * @author Emanuel Muckenhuber
 */
class PatchingOverlayDirArtifact extends AbstractArtifact<PatchableTargetsArtifact.PatchableTargetState, PatchingFileArtifact.DirectoryArtifactState>
        implements PatchingFileArtifact<PatchableTargetsArtifact.PatchableTargetState, PatchingFileArtifact.DirectoryArtifactState> {

    private final boolean bundles;

    protected PatchingOverlayDirArtifact(boolean bundles, PatchingArtifact<PatchingFileArtifact.DirectoryArtifactState, ? extends ArtifactState>... artifacts) {
        super(artifacts);
        this.bundles = bundles;
    }

    @Override
    public boolean process(PatchableTargetsArtifact.PatchableTargetState parent, PatchingArtifactProcessor processor) {
        if (bundles && !parent.isCheckBundles()) {
            return true;
        } else if (!parent.isCheckModules()) {
            return true;
        }
        final String patchID = parent.getPatchID();
        final DirectoryStructure structure = parent.getStructure();
        final File overlay = bundles ? structure.getBundlesPatchDirectory(patchID) : structure.getModulePatchDirectory(patchID);
        final PatchingFileArtifact.DirectoryArtifactState state = new PatchingFileArtifact.DirectoryArtifactState(overlay, this);
        return processor.process(this, state);
    }

}
