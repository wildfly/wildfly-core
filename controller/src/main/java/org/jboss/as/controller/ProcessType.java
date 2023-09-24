/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

/**
 * Holds the possible process types. This is used to identify what type of server we are running in.
 * {@link Extension}s can use this information to decide whether certain resources, operations or attributes
 * need to be present. {@link OperationStepHandler}s can use this to determine how to handle operations.
 */
public enum ProcessType {
    DOMAIN_SERVER(true, true),
    EMBEDDED_SERVER(true, false),
    STANDALONE_SERVER(true, false),
    HOST_CONTROLLER(false, true),
    EMBEDDED_HOST_CONTROLLER(false, true),
    APPLICATION_CLIENT(true, false),
    SELF_CONTAINED(true, false);

    private final boolean server;
    private final boolean domain;

    ProcessType(final boolean server, final boolean domain) {
        this.server = server;
        this.domain = domain;
    }

    /**
     * Returns true if the process is one of the server variants.
     *
     * @return Returns <tt>true</tt> if the process is a server. Returns <tt>false</tt> otherwise.
     */
    public boolean isServer() {
        return server;
    }

    /**
     * Returns true if the process is a host controller,
     *
     * @return Returns <tt>true</tt> if the process is a hostcontroller. Returns <tt>false</tt> otherwise.
     */
    public boolean isHostController() {
        return !isServer();
    }

    /**
     * Returns true if the process is a managed domain process.
     *
     * @return Returns <tt>true</tt> if the process is a managed domain process. Returns <tt>false</tt> otherwise.
     */
    public boolean isManagedDomain() {
        return domain;
    }
}
