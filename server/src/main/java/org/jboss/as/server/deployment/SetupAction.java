/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import java.util.Map;
import java.util.Set;

import org.jboss.msc.service.ServiceName;

/**
 * An action that sets up and tears down some form of context (e.g. the TCCL, JNDI context etc).
 * <p>
 * Implementations need to be thread safe, as multiple threads can be setting up and tearing down contexts at any given time.
 *
 * @author Stuart Douglas
 *
 */
public interface SetupAction {

    /**
     * Sets up the context. If this method throws an exception then the {@link #teardown(java.util.Map)} method will not be called, so this
     * method should be implmeneted in an atomic manner.
     *
     * @param properties data that a caller may provide to help configure the behavior of the action. May be {@code null}. An implementation
     *                   that expects certain properties should either require it is only used in cases where those properties will be provided,
     *                   or do something reasonable (e.g. become a no-op) if they are not provided.
     */
    void setup(Map<String, Object> properties);

    /**
     * Tears down the context that was set up and restores the previous context state.
     *
     * @param properties data that a caller may provide to help configure the behavior of the action. May be {@code null}. An implementation
     *                   that expects certain properties should either require it is only used in cases where those properties will be provided,
     *                   or do something reasonable (e.g. become a no-op) if they are not provided.
     */
    void teardown(Map<String, Object> properties);

    /**
     * Higher priority setup actions run first
     *
     * @return the priority
     */
    int priority();

    /**
     * Any dependencies that this action requires
     *
     * @return the names of the services this action requires. Will not be {@code null}.
     */
    Set<ServiceName> dependencies();

}
