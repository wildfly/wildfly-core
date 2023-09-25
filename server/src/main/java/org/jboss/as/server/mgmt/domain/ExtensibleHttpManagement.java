/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt.domain;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ResourceManager;

/**
 * API via which subsystems can extend the HTTP management interface functionality
 * by adding specific types of new contexts.
 *
 * @author Brian Stansberry
 */
public interface ExtensibleHttpManagement extends HttpManagement {

    /**
     * Add a context that serves static resources
     * @param contextName the name of the context. Cannot be {@code null} or empty
     * @param resourceManager manager to provide resources for the context. Cannot be {@code null}
     *
     * @throws IllegalArgumentException if either parameter is invalid
     * @throws IllegalStateException if there is already a context present named the same as {@code contextName}, either
     *                               added via this interface or otherwise
     */
    void addStaticContext(String contextName, ResourceManager resourceManager);

    /**
     * Add a context that remaps GET request paths to paths appropriate to the normal {@code /management}
     * context and then dispatches the request to the normal handling for the {@code /management} context.
     * POST requests result in a 405.
     * @param contextName the name of the context. Cannot be {@code null} or empty
     * @param remapper object that converts request paths
     *
     * @throws IllegalArgumentException if either parameter is invalid
     * @throws IllegalStateException if there is already a context present named the same as {@code contextName}, either
     *                               added via this interface or otherwise
     */
    void addManagementGetRemapContext(String contextName, PathRemapper remapper);

    /**
     * Add a context for a HTTP management handler.
     * @param contextName the name of the context. Cannot be {@code null} or empty
     * @param requiresSecurity {@code true} if the management handler requires a security realm.
     * @param managementHandler HTTP management handler. Cannot be {@code null}
     *
     * @throws IllegalArgumentException if either parameter is invalid
     * @throws IllegalStateException if there is already a context present named the same as {@code contextName}, either
     *                               added via this interface or otherwise
     */
    void addManagementHandler(String contextName, boolean requiresSecurity, HttpHandler managementHandler);

    /**
     * Remove a previously added context
     * @param contextName the name of the context. Cannot be {@code null} or empty
     *
     * @throws IllegalArgumentException if {@code contextName} is {@code null} or empty
     * @throws IllegalStateException if no context present named {@code contextName} was added via this interface
     */
    void removeContext(String contextName);

    /**
     * Converts a {@link HttpServerExchange#getRelativePath() context relative path} to a
     * path appropriate for use with the {@code /management} context. The context portion of
     * the path is not involved.
     */
    interface PathRemapper {
        /**
         * Converts {@code originalPath} to a path appropriate for use with the {@code /management} context.
         * The context portion of the path is not involved.
         * @param originalPath the original request path, relative to the context
         * @return the remapped path, which should be relative to the target context
         */
        String remapPath(String originalPath);
    }
}
