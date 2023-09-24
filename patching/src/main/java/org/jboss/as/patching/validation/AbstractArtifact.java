/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Alexey Loubyansky
 */
abstract class AbstractArtifact<P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> implements PatchingArtifact<P, S> {

    private Collection<PatchingArtifact<S, ? extends ArtifactState>> artifacts = Collections.emptyList();

    protected AbstractArtifact(PatchingArtifact<S, ? extends ArtifactState>... artifacts) {
        for (final PatchingArtifact<S, ? extends ArtifactState> artifact : artifacts) {
            addArtifact(artifact);
        }
    }

    @Override
    public Collection<PatchingArtifact<S, ? extends ArtifactState>> getArtifacts() {
        return artifacts;
    }

    protected void addArtifact(PatchingArtifact<S, ? extends ArtifactState> artifact) {
        assert artifact != null;
        switch (artifacts.size()) {
            case 0:
                artifacts = Collections.<PatchingArtifact<S, ? extends ArtifactState>>singletonList(artifact);
                break;
            case 1:
                artifacts = new ArrayList<PatchingArtifact<S, ? extends ArtifactState>>(artifacts);
            default:
                artifacts.add(artifact);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj == this);
    }

}
