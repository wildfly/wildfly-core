/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access;

/**
 * Interface for non-standard implementations of the {@link Authorizer} super-interface.
 * Custom authorizers will use this interface to allow integration with the WildFly management
 * layer.
 * <p>
 * Implementations of this interface must be loadable via the {@code java.util.ServiceLoader} mechanism.
 * </p>
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface CustomAuthorizer extends Authorizer {

    /**
     * Provides a reference to the {@link AuthorizerConfiguration} to the authorizer. This allows
     * the authorizer to take advantage of access control configuration information provided via
     * the WildFly management API. How, or even if, this information is used is up to the authorizer
     * implementation.
     * <p>
     * This method will be called before any methods in the {@code Authorizer} interface are called.
     * </p>
     *
     * @param configuration the configuration. Will not be {@code null}
     *
     * @return a description of this authorizer
     * @throws IllegalStateException if the configuration has already been set
     */
    AuthorizerDescription setAuthorizerConfiguration(AuthorizerConfiguration configuration);

    /**
     * Notification that the authorizer is no longer in use and should shut down.
     */
    void shutdown();

}
