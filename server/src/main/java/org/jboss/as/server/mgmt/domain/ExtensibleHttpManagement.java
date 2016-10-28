/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.server.mgmt.domain;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ResourceManager;

/**
 * API via which subsystems can extend the HTTP management interface functionality
 * by adding specific types of new contexts.
 *
 * @author Brian Stansberry
 *
 * @deprecated Experimental; may be removed or incompatibly modified in a future minor or major release
 */
@Deprecated
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
