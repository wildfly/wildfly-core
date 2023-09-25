/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;

import org.jboss.as.controller.OperationFailedException;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface ProcessEnvironmentSystemPropertyUpdater {
    /**
     * Gets whether updating the runtime system properties with the given property is allowed.
     *
     * @param propertyName  the name of the property. Cannot be {@code null}
     * @param propertyValue the value of the property. May be {@code null}
     * @param bootTime {@code true} if the process is currently booting
     *
     * @return {@code true} if the update can be applied to the runtime system properties; {@code} false if it
     *         should just be stored in the persistent configuration and the process should be put into
     *         {@link org.jboss.as.controller.ControlledProcessState.State#RELOAD_REQUIRED reload-required state}.
     *
     * @throws OperationFailedException if a change to the given property is not allowed at all; e.g. changing
     *                                  {@code jboss.server.base.dir} after primordial boot is not allowed; the
     *                                  property can only be set from the command line
     */
    boolean isRuntimeSystemPropertyUpdateAllowed(String propertyName,
                                                                    String propertyValue,
                                                                    boolean bootTime) throws OperationFailedException;

    /**
     * Notifies this {@code ProcessEnvironment} that the runtime value of the given system property has been updated,
     * allowing it to update any state that was originally set via the system property during primordial process boot.
     * This method should only be invoked after a call to {@link #isRuntimeSystemPropertyUpdateAllowed(String, String, boolean)}
     * has returned {@code true}.
     *
     * @param propertyName  the name of the property. Cannot be {@code null}
     * @param propertyValue the value of the property. May be {@code null}
     */
    void systemPropertyUpdated(String propertyName, String propertyValue);
}
