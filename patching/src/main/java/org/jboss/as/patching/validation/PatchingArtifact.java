/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import java.util.Collection;

/**
 * A generic validatable artifact as part of the patching state. All artifacts have a unique identity, but can have
 * a different {@code ArtifactState} depending on the context.
 *
 * @author Alexey Loubyansky
 * @author Emanuel Muckenhuber
 */
public interface PatchingArtifact<P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> {

    /**
     * Process the artifact and push it to the {@code PatchingArtifactProcessor} for further analysis.
     *
     * @param parent    the parent state
     * @param processor the processor
     * @return whether the current artifact and it's children are valid
     */
    boolean process(final P parent, final PatchingArtifactProcessor processor);

    /**
     * Get the associated child artifacts.
     *
     * @return the child artifacts
     */
    Collection<PatchingArtifact<S, ? extends ArtifactState>> getArtifacts();

    interface ArtifactState {

        /**
         * Check if a state is consistent.
         *
         * @param context the validation context
         * @return whether the artifact is valid
         */
        boolean isValid(PatchingArtifactValidationContext context);

    }

}
