/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Default implementation for DeploymentUnitContext.
 *
 * @author John E. Bailey
 */
class DeploymentUnitImpl extends SimpleAttachable implements DeploymentUnit {
    private final DeploymentUnit parent;
    private final String name;
    private final ServiceRegistry serviceRegistry;

    /**
     * Construct a new instance.
     *
     * @param parent the parent (enclosing) deployment unit, if any
     * @param name the deployment unit name
     * @param serviceRegistry the service registry
     */
    DeploymentUnitImpl(final DeploymentUnit parent, final String name, final ServiceRegistry serviceRegistry) {
        this.parent = parent;
        this.name = name;
        this.serviceRegistry = serviceRegistry;
    }

    public ServiceName getServiceName() {
        if (parent != null) {
            return Services.deploymentUnitName(parent.getName(), name);
        } else {
            return Services.deploymentUnitName(name);
        }
    }

    /** {@inheritDoc} */
    public DeploymentUnit getParent() {
        return parent;
    }

    /** {@inheritDoc} */
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    /** {@inheritDoc} */
    public String toString() {
        if (parent != null) {
            return String.format("subdeployment \"%s\" of %s", name, parent);
        } else {
            return String.format("deployment \"%s\"", name);
        }
    }
}
