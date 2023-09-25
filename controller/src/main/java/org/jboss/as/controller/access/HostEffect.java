/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access;

import java.util.Set;

import org.jboss.as.controller.PathAddress;

/**
 * Encapsulates information about the relationship of a resource to hosts in a domain.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface HostEffect {

    /**
     * Gets the address of the resource whose hosts relationships are described
     * by this object.
     *
     * @return the resource address. Will not be {@code null}
     */
    PathAddress getResourceAddress();

    /**
     * Gets whether the resource logically affects all hosts, including hosts that may not presently exist.
     * <p>
     * Domain level resources (i.e. those that persist configuration to domain.xml) logically affect all hosts
     * even if the servers running on some or all of the current set of hosts are not affected by the resource.
     * </p>
     *
     * @return {@code true} if the resource logically affects all hosts; {@code false} if the resource is
     *         logically limited to a set of hosts.
     */
    boolean isHostEffectGlobal();

    /**
     * Gets whether the resource is related to a specific server (either a server resource or a server-config).
     *
     * @return {@code true} if the resource relates to a specific server.
     */
    boolean isServerEffect();

    /**
     * Gets the names of the hosts affected by this resource, or {@code null} if
     * {@link #isHostEffectGlobal()} returns {@code true}
     *
     * @return the names of the hosts, or {@code null} if {@link #isHostEffectGlobal()} returns
     *         {@code true}. Will not return {@code null} if {@link #isHostEffectGlobal()} returns
     *         {@code false}, although it may return an empty set.
     */
    Set<String> getAffectedHosts();
}
