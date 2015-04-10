/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
