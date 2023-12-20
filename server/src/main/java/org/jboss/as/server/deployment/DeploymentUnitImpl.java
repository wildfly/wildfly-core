/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.as.version.Stability;
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
    private final Stability stability;

    /**
     * Construct a new instance.
     *
     * @param parent the parent (enclosing) deployment unit, if any
     * @param name the deployment unit name
     * @param serviceRegistry the service registry
     * @param stability the stability level of the current process
     */
    DeploymentUnitImpl(final DeploymentUnit parent, final String name, final ServiceRegistry serviceRegistry, Stability stability) {
        this.parent = parent;
        this.name = name;
        this.serviceRegistry = serviceRegistry;
        this.stability = stability;
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

    @Override
    public Stability getStability() {
        return this.stability;
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
