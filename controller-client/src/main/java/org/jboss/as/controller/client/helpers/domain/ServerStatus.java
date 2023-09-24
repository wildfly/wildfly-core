/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

/**
 * Status of server.
 *
 * @author Brian Stansberry
 */
public enum ServerStatus {

    /** The server is disabled; i.e. configured not to start automatically */
    DISABLED,
    /** The server is starting */
    STARTING,
    /** The server is started */
    STARTED,
    /** The server is stopping */
    STOPPING,
    /** The server is stopped */
    STOPPED,
    /** The server failed to start */
    FAILED,
    /**
     * The status of the server is currently unknown. This is the status of
     * any server whose host controller is currently unreachable.
     */
    UNKNOWN,
    /** Status indicating the host controller does not recognize the server name */
    DOES_NOT_EXIST
}
