/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Strategy for building a deployment unit phase.
 * @author Paul Ferraro
 */
public interface DeploymentUnitPhaseBuilder {

    /**
     * Builds a deployment phase.
     * @param target a service target
     * @param name the service name of the deployment phase
     * @param service the service providing the deployment phase
     * @return a service builder
     */
    <T> ServiceBuilder<T> build(ServiceTarget target, ServiceName name, Service<T> service);
}
