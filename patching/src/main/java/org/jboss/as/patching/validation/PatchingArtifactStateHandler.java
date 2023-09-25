/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

/**
 * State handler for patching artifacts.
 *
 * @author Alexey Loubyansky
 */
public interface PatchingArtifactStateHandler<S extends PatchingArtifact.ArtifactState> {

    /**
     * Handle the state after all children have been validated.
     *
     * @param state the validated state
     */
    void handleValidatedState(S state);

}
