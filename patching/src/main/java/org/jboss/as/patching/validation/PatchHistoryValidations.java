/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.logging.PatchLogger;

/**
 * Utility class to validate the patched state and history.
 *
 * @author Emanuel Muckenhuber
 */
public final class PatchHistoryValidations {

    private PatchHistoryValidations() {
        //
    }

    /**
     * Validate the consistency of patches to the point we rollback.
     *
     * @param patchID the patch id which gets rolled back
     * @param identity the installed identity
     * @throws PatchingException
     */
    public static void validateRollbackState(final String patchID, final InstalledIdentity identity) throws PatchingException {
        final Set<String> validHistory = processRollbackState(patchID, identity);
        if (patchID != null && !validHistory.contains(patchID)) {
            throw PatchLogger.ROOT_LOGGER.patchNotFoundInHistory(patchID);
        }
    }

    private static Set<String> processRollbackState(final String patchID, final InstalledIdentity identity) throws PatchingException {
        final Set<String> validHistory = new HashSet<String>();
        final PatchHistoryIterator.Builder builder = PatchHistoryIterator.Builder.create(identity);
        final HistoryProcessor processor = new HistoryProcessor() {

            boolean includeCurrent = true;
            boolean proceed = true;
            boolean found = false;

            @Override
            protected boolean includeCurrent() {
                return includeCurrent;
            }

            @Override
            protected boolean canProceed() {
                return proceed;
            }

            @Override
            protected <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> boolean handleError(PatchingArtifact<P, S> artifact, S state) {
                if (artifact == PatchingArtifacts.HISTORY_DIR
                        || artifact == PatchingArtifacts.PATCH_XML
                        || artifact == PatchingArtifacts.ROLLBACK_XML
                        || artifact == PatchingArtifacts.MISC_BACKUP) {
                    // If parts of the history are is missing we can rollback to this patch, but no further
                    proceed = false;
                    return found;
                } else {
                    includeCurrent = false;
                    proceed = false;
                    return false;
                }
            }

            @Override
            protected void processedPatch(String patch) {
                validHistory.add(patch);
                if (patch.equals(patchID)) {
                    // We found the patch we are rolling back, and continue to the next patch
                    found = true;
                } else if (found) {
                    proceed = false;
                }
            }
        };
        processor.process(builder.iterator());
        return validHistory;
    }


    abstract static class HistoryProcessor implements PatchingValidationErrorHandler {

        private final List<String> errors = new ArrayList<String>();

        /**
         * Whether the current patch is valid.
         *
         * @return {@code true} if the current patch is valid, {@code false} otherwise
         */
        protected abstract boolean includeCurrent();

        /**
         * Whether we can proceed processing the next patch.
         *
         * @return {@code true} if the next patch can be processed, {@code false} otherwise
         */
        protected abstract boolean canProceed();

        /**
         * Handle all errors.
         *
         * @param artifact the processed artifact in error
         * @param state    the artifact state
         * @param <P>
         * @param <S>
         * @param {@code true} if the error can be ignored, {@code false otherwise}
         */
        protected abstract <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> boolean handleError(PatchingArtifact<P, S> artifact, S state);

        /**
         * Callback for valid patches.
         *
         * @param patch the patch id
         */
        protected abstract void processedPatch(String patch);

        @Override
        public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addError(PatchingArtifact<P, S> artifact, S state) {
            if (!handleError(artifact, state)) {
                errors.add(PatchLogger.ROOT_LOGGER.artifactInError(state));
            }
        }

        @Override
        public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addInconsistent(PatchingArtifact<P, S> artifact, S current) {
            if (!handleError(artifact, current)) {
                errors.add(PatchLogger.ROOT_LOGGER.inconsistentArtifact(current));
            }
        }

        @Override
        public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addMissing(PatchingArtifact<P, S> artifact, S state) {
            if (!handleError(artifact, state)) {
                errors.add(PatchLogger.ROOT_LOGGER.missingArtifact(state));
            }
        }

        protected void process(PatchHistoryIterator iterator) throws PatchingException {
             while (iterator.hasNext() && canProceed()) {
                 final String patch = iterator.next(this);
                 if (includeCurrent()) {
                     processedPatch(patch);
                 }
             }
            // If there are errors, fail
            if (!errors.isEmpty()) {
                throw new PatchingException(errors.toString());
            }
        }

    }

    static class PatchingArtifactStateHandlers {

        // A map of artifact state handlers for each artifact. Generics FTW!
        private final Map<PatchingArtifact<? extends PatchingArtifact.ArtifactState, ? extends PatchingArtifact.ArtifactState>, PatchingArtifactStateHandler<? extends PatchingArtifact.ArtifactState>> handlers
                = new HashMap<PatchingArtifact<? extends PatchingArtifact.ArtifactState, ? extends PatchingArtifact.ArtifactState>, PatchingArtifactStateHandler<? extends PatchingArtifact.ArtifactState>>();


        <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void put(PatchingArtifact<P, S> artifact, PatchingArtifactStateHandler<S> handler) {
            assert !handlers.containsKey(artifact);
            handlers.put(artifact, handler);
        }

        <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> PatchingArtifactStateHandler<S> get(final PatchingArtifact<P, S> artifact) {
            return (PatchingArtifactStateHandler<S>) handlers.get(artifact);
        }

    }
}
