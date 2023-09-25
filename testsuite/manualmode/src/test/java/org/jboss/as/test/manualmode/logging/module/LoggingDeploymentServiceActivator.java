/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceRegistryException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingDeploymentServiceActivator implements ServiceActivator {
    public static final Logger LOGGER = Logger.getLogger(LoggingDeploymentServiceActivator.class);
    public static final String DEFAULT_MESSAGE = "This is a test message";

    @Override
    public void activate(final ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        for (Logger.Level level : Logger.Level.values()) {
            LOGGER.log(level, DEFAULT_MESSAGE);
        }
    }
}
