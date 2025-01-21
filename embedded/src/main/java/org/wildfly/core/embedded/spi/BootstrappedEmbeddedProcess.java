/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded.spi;

import java.util.concurrent.Executor;

import org.jboss.msc.service.ServiceContainer;

/**
 * Provides access to objects used to interact with a started embedded process.
 */
public interface BootstrappedEmbeddedProcess {

    /**
     * Gets the JBoss MSC {@link ServiceContainer} used by the embedded process.
     * @return the service container. Will not return {@code null}.
     */
    ServiceContainer getServiceContainer();

    /**
     * Get the object used to track the {@link EmbeddedProcessState state} of the embedded process.
     * @return the state notifier. Will not return {@code null}.
     */
    ProcessStateNotifier getProcessStateNotifier();

    /**
     * Gets a factory for {@link EmbeddedModelControllerClientFactory#createEmbeddedClient(Executor) creating management clients}
     * to manage with the embedded process.
     *
     * @return the factory. May return {@code null} if the embedded process is not in {@link EmbeddedProcessState#RUNNING} state.
     */
    EmbeddedModelControllerClientFactory getModelControllerClientFactory();

    /**
     * Closes out the use of the embedded process. This method must be called when the
     * embedding application is done with the embedded process.
     */
    void close();
}
