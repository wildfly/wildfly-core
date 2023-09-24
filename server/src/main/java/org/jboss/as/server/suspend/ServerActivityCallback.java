/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

/**
 * Callback interface that subsystems and other entry points can use to let the
 * suspend controller know that all requests have finished.
 *
 * @author Stuart Douglas
 */
public interface ServerActivityCallback {

    /**
     * Method that is invoked when the relevant entry point is done suspended
     */
    void done();
}
