/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.deployment.broken;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * ServiceActivator that installs itself as a service and sets a set of system
 * properties read from a "service-activator-deployment.properties" resource in the deployment.
 * If no resource is available, sets a default property.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ServiceActivatorDeployment implements ServiceActivator, Service<Void> {

    public static final ServiceName SERVICE_NAME = ServiceName.of("test", "deployment", "broken");
    public static final String FAIL_SYS_PROP = "test.deployment.broken.fail";
    public static final String FAILURE_MESSAGE = "configured to fail";

    @Override
    public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        serviceActivatorContext.getServiceTarget().addService(SERVICE_NAME, this).install();
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        Boolean fail = Boolean.getBoolean(FAIL_SYS_PROP);
        if (fail) {
            throw new StartException(FAILURE_MESSAGE);
        }
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }
}
