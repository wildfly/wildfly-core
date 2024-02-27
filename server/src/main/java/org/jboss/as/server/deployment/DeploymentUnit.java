/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.version.Stability;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * The deployment unit.  This object retains data which is persistent for the life of the
 * deployment.
 */
public interface DeploymentUnit extends Attachable, FeatureRegistry {

    /**
     * Get the service name of the root deployment unit service.
     *
     * @return the service name
     */
    ServiceName getServiceName();

    /**
     * Get the deployment unit of the parent (enclosing) deployment.
     *
     * @return the parent deployment unit, or {@code null} if this is a top-level deployment
     */
    DeploymentUnit getParent();

    /**
     * Get the simple name of the deployment unit.
     *
     * @return the simple name
     */
    String getName();

    /**
     * Get the service registry.
     *
     * @return the service registry
     */
    ServiceRegistry getServiceRegistry();

    // TODO Remove this once integrated into WF-full
    @Override
    default Stability getStability() {
        return Stability.DEFAULT;
    }
}
