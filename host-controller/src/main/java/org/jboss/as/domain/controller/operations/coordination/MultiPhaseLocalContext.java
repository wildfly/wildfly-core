/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import org.jboss.dmr.ModelNode;

/**
 * Stores contextual information for the local process aspects of
 * a multi-phase operation executing on the domain.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
final class MultiPhaseLocalContext {

    private final boolean coordinator;
    private final ModelNode localResult = new ModelNode();
    private final ModelNode localServerOps = new ModelNode();

    MultiPhaseLocalContext(boolean coordinator) {
        this.coordinator = coordinator;
    }

    public boolean isCoordinator() {
        return coordinator;
    }

    ModelNode getLocalResponse() {
        return localResult;
    }

    ModelNode getLocalServerOps() {
        return localServerOps;
    }
}
