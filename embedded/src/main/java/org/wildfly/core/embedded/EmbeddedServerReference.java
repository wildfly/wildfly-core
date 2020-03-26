/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.embedded;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * Implements both {@link StandaloneServer} and {@link HostController}.
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 * @author Thomas.Diesler@jboss.com
 *
 * @deprecated use the {@link EmbeddedManagedProcess}, {@link StandaloneServer} or {@link HostController} interface
 *             and not this implementation class
 */
@Deprecated
public final class EmbeddedServerReference implements StandaloneServer, HostController {

    private final EmbeddedManagedProcess delegate;
    private final boolean hostController;

    EmbeddedServerReference(final EmbeddedManagedProcess delegate, boolean hostController) {
        this.delegate = delegate;
        this.hostController = hostController;
    }

    @Override
    public void start()  {
        try {
            delegate.start();
        } catch (EmbeddedProcessStartException e) {
            // TODO This logger method is badly named.
            throw EmbeddedLogger.ROOT_LOGGER.cannotInvokeStandaloneServer(e, "start");
        }
    }

    @Override
    public void stop()  {
        delegate.stop();
    }

    @Override
    public String getProcessState() {
        return delegate.getProcessState();
    }

    @Override
    public ModelControllerClient getModelControllerClient()  {
        return delegate.getModelControllerClient();
    }

    public StandaloneServer getStandaloneServer() {
        return (StandaloneServer) delegate;
    }

    public HostController getHostController() {
        return (HostController) delegate;
    }

    public boolean isHostController() {
        return hostController;
    }
}
