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

import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.deployments.resources.LoggingDeploymentResources;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentResourceSupport;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logmanager.Configurator;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.modules.Module;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Process a deployment and ensures a logging configuration service has been added.
 * <p>
 * Note that in some cases the assumed logging configuration will be wrong. If a deployment is using a custom log
 * manager, such as logback, the configuration reported on the deployment will be the assumed configuration.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingDeploymentResourceProcessor implements DeploymentUnitProcessor {

    /**
     * The attachment key used to attach the service.
     */
    static final AttachmentKey<LoggingConfigurationService> LOGGING_CONFIGURATION_SERVICE_KEY = AttachmentKey.create(LoggingConfigurationService.class);

    @Override
    public final void deploy(final DeploymentPhaseContext phaseContext) {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (deploymentUnit.hasAttachment(Attachments.MODULE)) {
            LoggingConfigurationService loggingConfigurationService;
            if (deploymentUnit.hasAttachment(LOGGING_CONFIGURATION_SERVICE_KEY)) {
                loggingConfigurationService = deploymentUnit.getAttachment(LOGGING_CONFIGURATION_SERVICE_KEY);
                // Remove the attachment as it should no longer be needed
                deploymentUnit.removeAttachment(LOGGING_CONFIGURATION_SERVICE_KEY);
            } else {
                // Get the module
                final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
                // Set the deployments class loader to ensure we get the correct log context
                final ClassLoader current = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
                try {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(module.getClassLoader());
                    LogContextConfiguration logContextConfiguration = null;
                    final LogContext logContext = LogContext.getLogContext();
                    final Configurator configurator = logContext.getAttachment(CommonAttributes.ROOT_LOGGER_NAME, Configurator.ATTACHMENT_KEY);
                    if (configurator instanceof LogContextConfiguration) {
                        logContextConfiguration = (LogContextConfiguration) configurator;
                    } else if (configurator instanceof PropertyConfigurator) {
                        logContextConfiguration = ((PropertyConfigurator) configurator).getLogContextConfiguration();
                    }
                    loggingConfigurationService = new LoggingConfigurationService(logContextConfiguration, "default");
                } finally {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(current);
                }
            }

            final DeploymentResourceSupport deploymentResourceSupport = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_RESOURCE_SUPPORT);
            // Register the resources
            LoggingDeploymentResources.registerDeploymentResource(deploymentResourceSupport, loggingConfigurationService);
            phaseContext.getServiceTarget()
                    .addService(deploymentUnit.getServiceName().append("logging", "configuration"), loggingConfigurationService)
                    .install();
        }
    }

    @Override
    public final void undeploy(final DeploymentUnit context) {
    }
}
