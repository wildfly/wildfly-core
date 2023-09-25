/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

/**
 * An artifact which has been created by it's parent, but has different child {@code PatchingArtifact}s.
 *
 * @author Emanuel Muckenhuber
 */
class CreatedArtifact<T extends PatchingArtifact.ArtifactState> extends AbstractArtifact<T, T> {

    protected CreatedArtifact(PatchingArtifact<T, ? extends ArtifactState>... artifacts) {
        super(artifacts);
    }

    @Override
    public boolean process(T parent, PatchingArtifactProcessor processor) {
        return true;
    }

}
