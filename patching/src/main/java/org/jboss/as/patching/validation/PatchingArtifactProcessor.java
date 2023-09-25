/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;


/**
 * The artifact processor.
 *
 * @author Emanuel Muckenhuber
 */
interface PatchingArtifactProcessor {

    /**
     * Process an artifact. This validates the current {@code ArtifactState} and processes the associated
     * {@link PatchingArtifact#getArtifacts()} ()}. This returns {@code false} if there should no be no further processing
     * in {@link PatchingArtifact#process(org.jboss.as.patching.validation.PatchingArtifact.ArtifactState, PatchingArtifactProcessor)}.
     *
     * @param artifact the artifact to process
     * @param state    the parent artifact state
     * @param <P>      the parent artifact state type
     * @param <S>      the current artifact state type
     * @return whether the processing should continue or not
     */
    <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> boolean process(final PatchingArtifact<P, S> artifact, S state);

    /**
     * Get the parent artifact in the call stack. All artifacts have a unique identity and can only be present once
     * as part of the processing.
     *
     * @param artifact the artifact type
     * @param <P>      the parent artifact state type
     * @param <S>      the current artifact state type
     * @return the parent artifact
     */
    <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> S getParentArtifact(final PatchingArtifact<P, S> artifact);

    /**
     * Get the validation context.
     *
     * @return the validation context
     */
    PatchingArtifactValidationContext getValidationContext();
}
