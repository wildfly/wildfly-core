/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import java.io.IOException;
import java.util.List;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.metadata.RollbackPatch;

/**
 * Artifact validating that the overall patches are consistent with the rollback information.
 *
 * @author Emanuel Muckenhuber
 */
class RollbackTargetArtifact extends AbstractArtifact<PatchingXmlArtifact.XmlArtifactState<RollbackPatch>, RollbackTargetArtifact.State> {

    @Override
    public boolean process(PatchingXmlArtifact.XmlArtifactState<RollbackPatch> parent, PatchingArtifactProcessor processor) {
        final RollbackPatch patch = parent.getPatch();
        final PatchingArtifacts.PatchID patchID = processor.getParentArtifact(PatchingArtifacts.HISTORY_RECORD);
        final InstalledIdentity identity = patch.getIdentityState();
        processor.getValidationContext().setCurrentPatchIdentity(identity);
        final State state = new State(identity, patchID);
        if (identity == null) {
            processor.getValidationContext().getErrorHandler().addMissing(PatchingArtifacts.ROLLBACK_TARGET, state);
            return false;
        } else {
            return processor.process(this, state);
        }
    }

    static class State implements PatchingArtifact.ArtifactState {

        private final InstalledIdentity rollbackIdentity;
        private final PatchingArtifacts.PatchID reference;

        State(InstalledIdentity rollbackIdentity, PatchingArtifacts.PatchID reference) {
            this.rollbackIdentity = rollbackIdentity;
            this.reference = reference;
        }

        @Override
        public boolean isValid(PatchingArtifactValidationContext context) {
            try {
                // Check the target state we are rolling back to
                final PatchableTarget.TargetInfo target = rollbackIdentity.getIdentity().loadTargetInfo();
                final List<String> patches = target.getPatchIDs();
                final String rollbackTo;
                if (patches.isEmpty()) {
                    rollbackTo = target.getCumulativePatchID();
                } else {
                    rollbackTo = patches.get(0);
                }
                final String ref = reference.getNextPatchID();
                if (rollbackTo.equals(ref)) {
                    return true;
                } else if (ref == null && Constants.BASE.equals(rollbackTo)) {
                    return true;
                } else {
                    context.getErrorHandler().addInconsistent(PatchingArtifacts.ROLLBACK_TARGET, this);
                }
            } catch (IOException e) {
                context.getErrorHandler().addError(PatchingArtifacts.ROLLBACK_TARGET, this);
            }
            return false;
        }

        @Override
        public String toString() {
            return reference.getPatchID() != null ? reference.getPatchID() : Constants.BASE;
        }
    }

}
