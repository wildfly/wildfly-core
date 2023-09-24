/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller;

/**
 * @author Emanuel Muckenhuber
 */
public interface HostRegistrations {

    /**
     * Add a host event.
     *
     * @param hostName the host id
     * @param event the event
     */
    void addHostEvent(final String hostName, HostConnectionInfo.Event event);

    /**
     * Get the host registration info.
     *
     * @param hostName the host name
     * @return the host info
     */
    HostConnectionInfo getHostInfo(String hostName);

    /**
     * Prune all expired host info.
     */
    void pruneExpired();

    /**
     * Prune all info for disconnected hosts.
     */
    void pruneDisconnected();

}
