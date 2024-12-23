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
    DOMAIN_SERVER(true, true, false),
    EMBEDDED_SERVER(true, false, true),
    STANDALONE_SERVER(true, false, false),
    HOST_CONTROLLER(false, true, false),
    EMBEDDED_HOST_CONTROLLER(false, true, true),
    APPLICATION_CLIENT(true, false, false),
    SELF_CONTAINED(true, false, false);

    private final boolean server;
    private final boolean domain;
    private final boolean embedded;

    ProcessType(final boolean server, final boolean domain, final boolean embedded) {
        this.server = server;
        this.domain = domain;
        this.embedded = embedded;
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

    /**
     * Gets whether the process is an embedded process.
     *
     * @return <tt>true</tt> if the process is an embedded process; <tt>false</tt> otherwise.
     */
    public boolean isEmbedded() {
        return embedded;
    }
}
