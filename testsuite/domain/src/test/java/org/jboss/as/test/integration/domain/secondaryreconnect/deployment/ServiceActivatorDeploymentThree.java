/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.secondaryreconnect.deployment;

/**
 * ServiceActivator that installs itself as a service and sets a set of system
 * properties read from a "service-activator-deployment.properties" resource in the deployment.
 * If no resource is available, sets a default property.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ServiceActivatorDeploymentThree extends ServiceActivatorBaseDeployment {
    public ServiceActivatorDeploymentThree() {
        super("three");
    }
}
