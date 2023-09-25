/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchMetadataResolver;
import org.jboss.as.patching.metadata.PatchXml;
import org.jboss.as.patching.metadata.RollbackPatch;

/**
 * Aggregation of available patching artifacts.
 *
 * @author Emanuel Muckenhuber
 */
public final class PatchingArtifacts {

    // module and bundle overlay directories
    public static final PatchingOverlayDirArtifact MODULE_OVERLAY = new PatchingOverlayDirArtifact(false);
    public static final PatchingOverlayDirArtifact BUNDLE_OVERLAY = new PatchingOverlayDirArtifact(true);
    public static final PatchingFileArtifact.ConcreteDirectoryArtifact MISC_BACKUP = new PatchingFileArtifact.ConcreteDirectoryArtifact(Constants.MISC);

    // Empty representation for a layer
    public static final CreatedArtifact<PatchableTargetsArtifact.PatchableTargetState> LAYER = new CreatedArtifact<PatchableTargetsArtifact.PatchableTargetState>(PatchingArtifacts.MODULE_OVERLAY, PatchingArtifacts.BUNDLE_OVERLAY);

    // The patch.xml validation
    public static final PatchableTargetsArtifact PATCH_CONTENTS = new PatchableTargetsArtifact(LAYER);
    public static final PatchingXmlArtifact<Patch> PATCH = new PatchingXmlArtifact<Patch>(PATCH_CONTENTS) {
        @Override
        protected Patch resolveMetaData(PatchMetadataResolver resolver) throws PatchingException {
            return resolver.resolvePatch(null, null);
        }
    };

    // The rollback.xml validation
    public static final RollbackTargetArtifact ROLLBACK_TARGET = new RollbackTargetArtifact();
    public static final PatchingXmlArtifact<RollbackPatch> ROLLBACK_PATCH = new PatchingXmlArtifact<RollbackPatch>(ROLLBACK_TARGET) {
        @Override
        protected RollbackPatch resolveMetaData(PatchMetadataResolver resolver) throws PatchingException {
            return (RollbackPatch) resolver.resolvePatch(null, null);
        }
    };

    // History dir artifacts
    public static final PatchingFileArtifact.ConcreteFileArtifact PATCH_XML = new PatchingFileArtifact.ConcreteFileArtifact(PatchXml.PATCH_XML, PATCH);
    public static final PatchingFileArtifact.ConcreteFileArtifact ROLLBACK_XML = new PatchingFileArtifact.ConcreteFileArtifact(PatchXml.ROLLBACK_XML, ROLLBACK_PATCH);
    public static final PatchingFileArtifact.ConcreteDirectoryArtifact CONFIGURATION_BACKUP = new PatchingFileArtifact.ConcreteDirectoryArtifact(Constants.CONFIGURATION);

    // The history dir artifact itself
    public static final PatchingHistoryDirArtifact HISTORY_DIR = new PatchingHistoryDirArtifact();

    // A record in the patch history
    public static final PatchingArtifact<PatchID, PatchID> HISTORY_RECORD = new CreatedArtifact<PatchID>(HISTORY_DIR);

    private PatchingArtifacts() {
        //
    }

    static class PatchID implements PatchingArtifact.ArtifactState {

        private final String patchID;
        private final String nextPatchID;

        PatchID(String patchID, String nextPatchID) {
            this.patchID = patchID;
            this.nextPatchID = nextPatchID;
        }

        public String getPatchID() {
            return patchID;
        }

        public String getNextPatchID() {
            return nextPatchID;
        }

        @Override
        public boolean isValid(PatchingArtifactValidationContext context) {
            return patchID != null;
        }
    }
}
