/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.deployments;

import org.wildfly.core.logmanager.config.LogContextConfiguration;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * A service use to query the logging configuration for the deployment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingConfigurationService implements Service<LogContextConfiguration> {
    private final LogContextConfiguration logContextConfiguration;
    private final String configuration;

    LoggingConfigurationService(final LogContextConfiguration logContextConfiguration, final String configuration) {
        this.logContextConfiguration = logContextConfiguration;
        this.configuration = configuration;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        // Nothing needed
    }

    @Override
    public void stop(final StopContext context) {
        // Nothing needed
    }

    @Override
    public LogContextConfiguration getValue() throws IllegalStateException, IllegalArgumentException {
        return logContextConfiguration;
    }

    /**
     * Returns an identifier for the configuration being used.
     * <p>
     * This will be used as the value for the address of a deployment resource.
     * </p>
     *
     * @return the configuration name
     */
    public String getConfiguration() {
        return configuration;
    }
}
