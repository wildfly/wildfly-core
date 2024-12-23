/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded.spi;

/**
 * Service interface that standalone server or host controller bootstrap logic can implement
 * to allow their type of process to be bootstrapped in an embedded environment.
 */
public interface EmbeddedProcessBootstrap {

    enum Type {
        STANDALONE_SERVER,
        HOST_CONTROLLER
    }

    /**
     * Gets the type of managed process this object bootstraps.
     * @return the type. Will not be {@code null}.
     */
    Type getType();

    /**
     * Bootstraps an embedded process based on the provided configuration.
     *
     * @param configuration configuration for the bootstrap. Cannot be {@code null}.
     *
     * @return container object providing the embedding code access to items needed to manage the embedded process
     *
     * @throws Exception if one occurs while bootstrapping the process.
     */
    BootstrappedEmbeddedProcess startup(EmbeddedProcessBootstrapConfiguration configuration) throws Exception;
}
