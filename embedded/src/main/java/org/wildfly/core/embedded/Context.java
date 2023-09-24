/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

/**
 * A context used to activate and restore the environment for embedded containers.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface Context {

    /**
     * Activates the context for the current embedded environment.
     */
    void activate();

    /**
     * Restores the previous context.
     */
    void restore();
}
