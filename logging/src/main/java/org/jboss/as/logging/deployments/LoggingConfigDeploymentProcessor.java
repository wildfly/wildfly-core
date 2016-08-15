/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.xml.DOMConfigurator;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.logmanager.WildFlyLogContextSelector;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.modules.Module;
import org.jboss.modules.Resource;
import org.wildfly.security.manager.WildFlySecurityManager;

import static org.jboss.as.server.loaders.Utils.getResourceName;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class LoggingConfigDeploymentProcessor extends AbstractLoggingDeploymentProcessor implements DeploymentUnitProcessor {

    /**
     * @deprecated use the {@code use-deployment-logging-config} on the root resource
     */
    @Deprecated
    public static final String PER_DEPLOYMENT_LOGGING = "org.jboss.as.logging.per-deployment";

    private static final Charset ENCODING = StandardCharsets.UTF_8;
    private static final String LOG4J_PROPERTIES = "log4j.properties";
    private static final String LOG4J_XML = "log4j.xml";
    private static final String JBOSS_LOG4J_XML = "jboss-log4j.xml";
    private static final String DEFAULT_PROPERTIES = "logging.properties";
    private static final String JBOSS_PROPERTIES = "jboss-logging.properties";
    private static final Object CONTEXT_LOCK = new Object();
    private static final String[] CONFIG_FILES = {
            LOG4J_PROPERTIES, LOG4J_XML, JBOSS_LOG4J_XML, JBOSS_PROPERTIES, DEFAULT_PROPERTIES
    };

    private final String attributeName;
    private final boolean process;

    public LoggingConfigDeploymentProcessor(final WildFlyLogContextSelector logContextSelector, final String attributeName, final boolean process) {
        super(logContextSelector);
        this.attributeName = attributeName;
        this.process = process;
    }

    @Override
    protected void processDeployment(final DeploymentPhaseContext phaseContext, final DeploymentUnit deploymentUnit, final ResourceRoot root) throws DeploymentUnitProcessingException {
        boolean process = this.process;
        // Get the system properties
        final Properties systemProps = WildFlySecurityManager.getSystemPropertiesPrivileged();
        if (systemProps.containsKey(PER_DEPLOYMENT_LOGGING)) {
            LoggingLogger.ROOT_LOGGER.perDeploymentPropertyDeprecated(PER_DEPLOYMENT_LOGGING, attributeName);
            if (process) {
                process = Boolean.valueOf(WildFlySecurityManager.getPropertyPrivileged(PER_DEPLOYMENT_LOGGING, Boolean.toString(true)));
            } else {
                LoggingLogger.ROOT_LOGGER.perLoggingDeploymentIgnored(PER_DEPLOYMENT_LOGGING, attributeName, deploymentUnit.getName());
            }
        }
        LoggingConfigurationService loggingConfigurationService = null;

        // Check that per-deployment logging is not turned off
        if (process) {
            LoggingLogger.ROOT_LOGGER.trace("Scanning for logging configuration files.");
            final List<DeploymentUnit> subDeployments = getSubDeployments(deploymentUnit);
            // Check for a config file
            final Resource configFile = findConfigFile(root);
            if (configFile != null) {
                // Get the module
                final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
                // Create the log context and load into the selector for the module and keep a strong reference
                final LogContext logContext;
                if (isLog4jConfiguration(getResourceName(configFile.getName()))) {
                    logContext = LogContext.create(true);
                } else {
                    logContext = LogContext.create();
                }

                boolean processSubdeployments = true;
                // Configure the deployments logging based on the top-level configuration file
                loggingConfigurationService = configure(root, configFile, module.getClassLoader(), logContext);
                if (loggingConfigurationService != null) {
                    registerLogContext(deploymentUnit, module, logContext);
                } else {
                    processSubdeployments = false;
                }

                if (processSubdeployments) {
                    // Process the sub-deployments
                    for (DeploymentUnit subDeployment : subDeployments) {
                        if (subDeployment.hasAttachment(Attachments.DEPLOYMENT_ROOT)) {
                            processDeployment(phaseContext, subDeployment, subDeployment.getAttachment(Attachments.DEPLOYMENT_ROOT));
                        }
                        // No configuration file found, use the top-level configuration
                        if (subDeployment.hasAttachment(Attachments.MODULE) && !hasRegisteredLogContext(subDeployment)) {
                            final Module subDeploymentModule = subDeployment.getAttachment(Attachments.MODULE);
                            if (subDeploymentModule != null) {
                                registerLogContext(subDeployment, subDeploymentModule, logContext);
                            }
                        }
                        // Add the parent's logging service if it should be inherited
                        if (!subDeployment.hasAttachment(LoggingDeploymentResourceProcessor.LOGGING_CONFIGURATION_SERVICE_KEY)) {
                            subDeployment.putAttachment(LoggingDeploymentResourceProcessor.LOGGING_CONFIGURATION_SERVICE_KEY, loggingConfigurationService);
                        }
                    }
                }
            } else {
                // No configuration was found, process sub-deployments for configuration files
                for (DeploymentUnit subDeployment : subDeployments) {
                    if (subDeployment.hasAttachment(Attachments.DEPLOYMENT_ROOT)) {
                        processDeployment(phaseContext, subDeployment, subDeployment.getAttachment(Attachments.DEPLOYMENT_ROOT));
                    }
                }
            }
        }
        // Add the configuration service
        if (loggingConfigurationService != null) {
            // Add the service to the deployment unit
            deploymentUnit.putAttachment(LoggingDeploymentResourceProcessor.LOGGING_CONFIGURATION_SERVICE_KEY, loggingConfigurationService);
        }
    }

    /**
     * Finds the configuration file to be used and returns the first one found.
     * <p/>
     * Preference is for {@literal logging.properties} or {@literal jboss-logging.properties}.
     *
     * @param resourceRoot the resource to check.
     *
     * @return the configuration file if found, otherwise {@code null}.
     *
     * @throws DeploymentUnitProcessingException if an error occurs.
     */
    private Resource findConfigFile(ResourceRoot resourceRoot) throws DeploymentUnitProcessingException {
        // First check META-INF
        Resource result = findConfigFile(resourceRoot.getLoader(), "META-INF");
        if (result == null) {
            result = findConfigFile(resourceRoot.getLoader(), "WEB-INF/classes");
        }
        return result;
    }

    /**
     * Finds the configuration file to be used and returns the first one found.
     * <p/>
     * Preference is for {@literal logging.properties} or {@literal jboss-logging.properties}.
     *
     * @param loader the loader to inspect
     * @param path the path to inspect
     *
     * @return the configuration file if found, otherwise {@code null}
     *
     * @throws DeploymentUnitProcessingException if an error occurs.
     */
    private Resource findConfigFile(final ResourceLoader loader, final String path) throws DeploymentUnitProcessingException {
        final Iterator<Resource> configFiles = loader.iterateResources(path, false);
        Resource result = null;
        Resource configFile;
        while (configFiles.hasNext()) {
            configFile = configFiles.next();
            final String fileName = getResourceName(configFile.getName());
            if (!isConfigFile(fileName)) continue;
            if (DEFAULT_PROPERTIES.equals(fileName) || JBOSS_PROPERTIES.equals(fileName)) {
                if (result != null) {
                    LoggingLogger.ROOT_LOGGER.debugf("The previously found configuration file '%s' is being ignored in favour of '%s'", result, configFile);
                }
                return configFile;
            } else if (LOG4J_PROPERTIES.equals(fileName) || LOG4J_XML.equals(fileName) || JBOSS_LOG4J_XML.equals(fileName)) {
                result = configFile;
            }
        }
        return result;
    }

    private static boolean isConfigFile(final String candidate) {
        for (final String configFile : CONFIG_FILES) {
            if (candidate.equals(configFile)) return true;
        }
        return false;
    }

    /**
     * Configures the log context.
     *
     * @param configFile  the configuration file
     * @param classLoader the class loader to use for the configuration
     * @param logContext  the log context to configure
     *
     * @return {@code true} if the log context was successfully configured, otherwise {@code false}
     *
     * @throws DeploymentUnitProcessingException if the configuration fails
     */
    private LoggingConfigurationService configure(final ResourceRoot root, final Resource configFile, final ClassLoader classLoader, final LogContext logContext) throws DeploymentUnitProcessingException {
        InputStream configStream = null;
        try {
            LoggingLogger.ROOT_LOGGER.debugf("Found logging configuration file: %s", configFile);

            // Get the filename and open the stream
            final String fileName = getResourceName(configFile.getName());
            configStream = configFile.openStream();

            // Check the type of the configuration file
            if (isLog4jConfiguration(fileName)) {
                final ClassLoader current = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
                final LogContext old = logContextSelector.getAndSet(CONTEXT_LOCK, logContext);
                try {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
                    if (LOG4J_XML.equals(fileName) || JBOSS_LOG4J_XML.equals(fileName)) {
                        new DOMConfigurator().doConfigure(configStream, org.apache.log4j.JBossLogManagerFacade.getLoggerRepository(logContext));
                    } else {
                        final Properties properties = new Properties();
                        properties.load(new InputStreamReader(configStream, ENCODING));
                        new org.apache.log4j.PropertyConfigurator().doConfigure(properties, org.apache.log4j.JBossLogManagerFacade.getLoggerRepository(logContext));
                    }
                } finally {
                    logContextSelector.getAndSet(CONTEXT_LOCK, old);
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(current);
                }
                return new LoggingConfigurationService(null, resolveRelativePath(root, configFile));
            } else {
                // Create a properties file
                final Properties properties = new Properties();
                properties.load(new InputStreamReader(configStream, ENCODING));
                // Attempt to see if this is a J.U.L. configuration file
                if (isJulConfiguration(properties)) {
                    LoggingLogger.ROOT_LOGGER.julConfigurationFileFound(configFile.getName());
                } else {
                    // Load non-log4j types
                    final PropertyConfigurator propertyConfigurator = new PropertyConfigurator(logContext);
                    propertyConfigurator.configure(properties);
                    return new LoggingConfigurationService(propertyConfigurator.getLogContextConfiguration(), resolveRelativePath(root, configFile));
                }
            }
        } catch (Exception e) {
            throw LoggingLogger.ROOT_LOGGER.failedToConfigureLogging(e, configFile.getName());
        } finally {
            safeClose(configStream);
        }
        return null;
    }

    private static boolean isLog4jConfiguration(final String fileName) {
        return LOG4J_PROPERTIES.equals(fileName) || LOG4J_XML.equals(fileName) || JBOSS_LOG4J_XML.equals(fileName);
    }

    private static boolean isJulConfiguration(final Properties properties) {
        // First check for .levels as it's the cheapest
        if (properties.containsKey(".level")) {
            return true;
            // Check the handlers, in JBoss Log Manager they should be handler.HANDLER_NAME=HANDLER_CLASS,
            // J.U.L. uses fully.qualified.handler.class.property
        } else if (properties.containsKey("handlers")) {
            final String prop = properties.getProperty("handlers", "");
            if (prop != null && !prop.trim().isEmpty()) {
                final String[] handlers = prop.split("\\s*,\\s*");
                for (String handler : handlers) {
                    final String key = String.format("handler.%s", handler);
                    if (!properties.containsKey(key)) {
                        return true;
                    }
                }
            }
        }
        // Assume it's okay
        return false;
    }

    private static String resolveRelativePath(final ResourceRoot root, final Resource configFile) {
        final ResourceLoader loader = root.getLoader();
        if (loader.getPath() == null || loader.getPath().equals("")) {
            return loader.getRootName() + "/" + configFile.getName();
        } else {
            return getResourceName(root.getLoader().getPath()) + "/" + configFile.getName();
        }
    }

}
