/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.Optional;

import org.jboss.as.controller.services.path.PathManager;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} registration context.
 * @author Paul Ferraro
 */
public interface ManagementResourceRegistrationContext {

    /**
     * Gets whether it is valid for the extension to register resources, attributes or operations that do not
     * involve the persistent configuration, but rather only involve runtime services. Extensions should use this
     * method before registering such "runtime only" resources, attributes or operations. This
     * method is intended to avoid registering resources, attributes or operations on process types that
     * can not install runtime services.
     * @see {@link org.jboss.as.controller.ExtensionContext#isRuntimeOnlyRegistrationValid()}.
     * @return whether it is valid to register runtime resources, attributes, or operations.
     */
    boolean isRuntimeOnlyRegistrationValid();

    /**
     * Returns the optional {@link PathManager} of the process that is only present if the process is a {@link org.jboss.as.controller.ProcessType#isServer() server}.
     * @see org.jboss.as.controller.ExtensionContext#getPathManager()
     * @return an optional PathManager.
     */
    Optional<PathManager> getPathManager();
}
