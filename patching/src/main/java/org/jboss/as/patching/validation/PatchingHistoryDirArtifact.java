/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import java.io.File;

import org.jboss.as.patching.installation.InstalledIdentity;

/**
 * Artifact representing the patch history. This validates the patch.xml and rollback.xml. Misc files and overlays are
 * validated as part of the patch.xml, to determine whether they have to exist or not.
 *
 * @author Alexey Loubyansky
 * @author Emanuel Muckenhuber
 */
class PatchingHistoryDirArtifact extends AbstractArtifact<PatchingArtifacts.PatchID, PatchingFileArtifact.DirectoryArtifactState>
        implements PatchingFileArtifact<PatchingArtifacts.PatchID, PatchingFileArtifact.DirectoryArtifactState> {

    PatchingHistoryDirArtifact() {
        super(PatchingArtifacts.PATCH_XML, PatchingArtifacts.ROLLBACK_XML);
    }

    @Override
    public boolean process(PatchingArtifacts.PatchID parent, PatchingArtifactProcessor processor) {
        final InstalledIdentity identity = processor.getValidationContext().getInstalledIdentity();
        final File history = identity.getInstalledImage().getPatchHistoryDir(parent.getPatchID());
        final PatchingFileArtifact.DirectoryArtifactState state = new PatchingFileArtifact.DirectoryArtifactState(history, this);
        return processor.process(this, state);
    }

}

