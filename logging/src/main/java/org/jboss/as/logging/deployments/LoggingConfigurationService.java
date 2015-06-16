/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.logging.deployments;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.Services;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
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

    public LoggingConfigurationService(final LogContextConfiguration logContextConfiguration, final String configuration) {
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

    /**
     * Create the service name used for this service for the deployment.
     *
     * @param deployment the deployment for this service
     *
     * @return the service name
     */
    static ServiceName forDeployment(final DeploymentUnit deployment) {
        return deployment.getServiceName().append("logging", "configuration");
    }

    /**
     * Create the service name used for this service on a deployment.
     *
     * @param address the deployments address
     *
     * @return the service name
     */
    public static ServiceName forDeployment(final PathAddress address) {
        String deploymentName = null;
        String subdeploymentName = null;
        for (PathElement element : address) {
            if (ModelDescriptionConstants.DEPLOYMENT.equals(element.getKey())) {
                deploymentName = element.getValue();
            } else if (ModelDescriptionConstants.SUBDEPLOYMENT.endsWith(element.getKey())) {
                subdeploymentName = element.getValue();
            }
        }
        if (deploymentName == null) {
            throw LoggingLogger.ROOT_LOGGER.deploymentNameNotFound(address);
        }
        final ServiceName result;
        if (subdeploymentName == null) {
            result = Services.deploymentUnitName(deploymentName);
        } else {
            result = Services.deploymentUnitName(deploymentName, subdeploymentName);
        }
        return result.append("logging", "configuration");
    }
}
