/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.msc.service.ServiceName;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Services {

    private Services() {}

    /**
     * The base name for deployment services.
     */
    public static final ServiceName JBOSS_DEPLOYMENT = ServiceName.JBOSS.append("deployment");
    /**
     * The base name for deployment unit services and phase services.
     */
    public static final ServiceName JBOSS_DEPLOYMENT_UNIT = JBOSS_DEPLOYMENT.append("unit");
    /**
     * The base name for sub-deployment unit services and phase services.
     */
    public static final ServiceName JBOSS_DEPLOYMENT_SUB_UNIT = JBOSS_DEPLOYMENT.append("subunit");
    /**
     * The service name of the deployment chains service.
     */
    public static final ServiceName JBOSS_DEPLOYMENT_CHAINS = JBOSS_DEPLOYMENT.append("chains");
    /**
     * The service name of the deployment extension index service.
     */
    public static final ServiceName JBOSS_DEPLOYMENT_EXTENSION_INDEX = JBOSS_DEPLOYMENT.append("extension-index");

    /**
     * Get the service name of a top-level deployment unit.
     *
     * @param name the simple name of the deployment
     * @return the service name
     */
    public static ServiceName deploymentUnitName(String name) {
        return JBOSS_DEPLOYMENT_UNIT.append(name);
    }

    static ServiceName deploymentStatusName(ServiceName deploymentUnitName) {
        return deploymentUnitName.append("status");
    }

    /**
     * Get the service name of a subdeployment.
     *
     * @param parent the parent deployment name
     * @param name the subdeployment name
     * @return the service name
     */
    public static ServiceName deploymentUnitName(String parent, String name) {
        return JBOSS_DEPLOYMENT_SUB_UNIT.append(parent, name);
    }

    /**
     * Get the service name of a top-level deployment unit.
     *
     * @param name the simple name of the deployment
     * @param phase the deployment phase
     * @return the service name
     */
    public static ServiceName deploymentUnitName(String name, Phase phase) {
        return JBOSS_DEPLOYMENT_UNIT.append(name, phase.name());
    }

    /**
     * Get the service name of a subdeployment.
     *
     * @param parent the parent deployment name
     * @param name the subdeployment name
     * @param phase the deployment phase
     * @return the service name
     */
    public static ServiceName deploymentUnitName(String parent, String name, Phase phase) {
        return JBOSS_DEPLOYMENT_SUB_UNIT.append(parent, name, phase.name());
    }
}
