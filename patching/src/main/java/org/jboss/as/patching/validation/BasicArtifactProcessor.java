/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.patching.installation.InstalledIdentity;

/**
 * @author Emanuel Muckenhuber
 */
class BasicArtifactProcessor implements PatchingArtifactProcessor {

    private InstalledIdentity currentIdentity;
    private InstalledIdentity next;

    private final PatchHistoryValidations.PatchingArtifactStateHandlers handlers;
    private final PatchingArtifactValidationContext context;

    // The current node
    private Node current;

    public BasicArtifactProcessor(final InstalledIdentity installedIdentity, final PatchingValidationErrorHandler errorHandler,
                                  final PatchHistoryValidations.PatchingArtifactStateHandlers handlers) {
        assert installedIdentity != null;
        assert errorHandler != null;
        assert handlers != null;
        this.context = new PatchingArtifactValidationContext() {

            @Override
            public PatchingValidationErrorHandler getErrorHandler() {
                if (current.context != null) {
                    return current.context;
                }
                return errorHandler;
            }

            @Override
            public InstalledIdentity getInstalledIdentity() {
                return currentIdentity;
            }

            @Override
            public void setCurrentPatchIdentity(InstalledIdentity currentPatchIdentity) {
                next = checkNotNullParam("currentPatchIdentity", currentPatchIdentity);
            }

        };
        this.handlers = handlers;
        currentIdentity = next = installedIdentity;
    }

    protected <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> boolean processRoot(PatchingArtifact<P, S> artifact, S state, PatchingValidationErrorHandler context) {
        assert current == null;
        current = new Node(null, artifact, state, context);
        try {
            return doProcess(artifact, state);
        } finally {
            // Swap identity for the next patch-id
            currentIdentity = next;
            current = null;
        }
    }

    @Override
    public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> boolean process(PatchingArtifact<P, S> artifact, S state) {
        final Node old = current;
        current = new Node(old, artifact, state, old.context);
        try {
            return doProcess(artifact, state);
        } finally {
            current = old;
        }
    }

    public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> boolean doProcess(PatchingArtifact<P, S> artifact, S state) {
        final PatchingArtifactStateHandler<S> handler = getHandlerForArtifact(artifact);
        if (!state.isValid(getValidationContext())) {
            return false;
        }
        // Process each child artifact
        boolean valid = true;
        for (final PatchingArtifact<S, ? extends PatchingArtifact.ArtifactState> child : artifact.getArtifacts()) {
            if (!child.process(state, this)) {
                valid = false;
            }
        }
        if (valid && handler != null) {
            handler.handleValidatedState(state);
        }
        return valid;
    }

    /**
     * Get a state handler for a given patching artifact.
     *
     * @param artifact the patching artifact
     * @param <P>
     * @param <S>
     * @return the state handler, {@code null} if there is no handler registered for the given artifact
     */
    <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> PatchingArtifactStateHandler<S> getHandlerForArtifact(PatchingArtifact<P, S> artifact) {
        return handlers.get(artifact);
    }

    @Override
    public <P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> S getParentArtifact(PatchingArtifact<P, S> artifact) {
        Node node = current;
        while (node != null) {
            if (node.artifact == artifact) {
                return (S) node.state;
            }
            node = node.parent;
        }
        return null;
    }

    @Override
    public PatchingArtifactValidationContext getValidationContext() {
        return context;
    }

    static class Node<P extends PatchingArtifact.ArtifactState, S extends PatchingArtifact.ArtifactState> {

        private final S state;
        private final Node parent;
        private final PatchingArtifact<P, S> artifact;
        private final PatchingValidationErrorHandler context;

        Node(Node parent, PatchingArtifact<P, S> artifact, S state, PatchingValidationErrorHandler context) {
            this.state = state;
            this.parent = parent;
            this.artifact = artifact;
            this.context = context;
        }
    }

}
