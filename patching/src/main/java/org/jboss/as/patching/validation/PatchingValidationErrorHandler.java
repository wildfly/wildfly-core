/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

/**
 * The validation context.
 *
 * @author Alexey Loubyansky
 * @author Emanuel Muckenhuber
 */
public interface PatchingValidationErrorHandler {

    /**
     * Add an error when trying to load an artifact.
     *
     * @param artifact the artifact
     * @param state    the artifact state
     * @param <P>      the parent type
     * @param <S>      the current type
     */
    <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addError(PatchingArtifact<P, S> artifact, S state);

    /**
     * Add an inconsistent artifact.
     *
     * @param artifact the artifact
     * @param current  the artifact state
     * @param expected the artifact state
     * @param <P>      the parent type
     * @param <S>      the current type
     */
    <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addInconsistent(PatchingArtifact<P, S> artifact, S current);

    /**
     * Add a missing artifact.
     *
     * @param artifact the artifact
     * @param state    the artifact state
     * @param <P>      the parent type
     * @param <S>      the current type
     */
    <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addMissing(PatchingArtifact<P, S> artifact, S state);

    PatchingValidationErrorHandler DEFAULT = new PatchingValidationErrorHandler() {
        @Override
        public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addError(PatchingArtifact<P, S> artifact, S state) {
            throw new RuntimeException("error when processing artifact " + artifact);
        }

        @Override
        public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addInconsistent(PatchingArtifact<P, S> artifact, S current) {
            throw new RuntimeException("inconsistent artifact " + artifact + ": " + current);
        }

        @Override
        public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> void addMissing(PatchingArtifact<P, S> artifact, S state) {
            throw new RuntimeException("missing artifact " + artifact + ": " + state);
        }
    };

}
