/*
Copyright 2015 Red Hat, Inc.

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

package org.wildfly.core.embedded;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Base interface for an embedded process that can be managed with a {@link ModelControllerClient}.
 *
 * @author Thomas.Diesler@jboss.com
 * @author Brian Stansberry
 */
public interface EmbeddedManagedProcess {

    /**
     * Gets the client that can be used to manage the embedded process.
     *
     * @return the client, or {@code null} if the process is not started
     */
    ModelControllerClient getModelControllerClient();

    /**
     * Start the embedded process.
     *
     * @throws EmbeddedProcessStartException
     */
    void start() throws EmbeddedProcessStartException;

    /**
     * Stop the embedded process.
     */
    void stop();
}
