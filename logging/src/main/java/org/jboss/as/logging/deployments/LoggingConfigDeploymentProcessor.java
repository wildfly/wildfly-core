/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.deployments;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jboss.as.logging.logging.LoggingLogger;
import org.wildfly.core.logmanager.WildFlyLogContextSelector;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.logmanager.LogContext;
import org.wildfly.core.logmanager.config.LogContextConfiguration;
import org.jboss.modules.Module;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFileFilter;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingConfigDeploymentProcessor extends AbstractLoggingDeploymentProcessor implements DeploymentUnitProcessor {

    /**
     * @deprecated use the {@code use-deployment-logging-config} on the root resource
     */
    @Deprecated
    public static final String PER_DEPLOYMENT_LOGGING = "org.jboss.as.logging.per-deployment";

    private static final Charset ENCODING = StandardCharsets.UTF_8;
    private static final String DEFAULT_PROPERTIES = "logging.properties";
    private static final String JBOSS_PROPERTIES = "jboss-logging.properties";

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
            final VirtualFile configFile = findConfigFile(root);
            if (configFile != null) {
                // Get the module
                final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
                // Create the log context and load into the selector for the module and keep a strong reference
                final LogContext logContext = LogContext.create();
                // Add the log context for cleanup
                LoggingCleanupDeploymentProcessor.addResource(deploymentUnit, logContext);

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
                        if (!hasRegisteredLogContext(subDeployment)) {
                            final Module subDeploymentModule = subDeployment.getAttachment(Attachments.MODULE);
                            registerLogContext(subDeployment, subDeploymentModule, logContext);
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
    private VirtualFile findConfigFile(ResourceRoot resourceRoot) throws DeploymentUnitProcessingException {
        final VirtualFile root = resourceRoot.getRoot();
        // First check META-INF
        VirtualFile file = root.getChild("META-INF");
        VirtualFile result = findConfigFile(file);
        if (result == null) {
            file = root.getChild("WEB-INF/classes");
            result = findConfigFile(file);
        }
        return result;
    }

    /**
     * Finds the configuration file to be used and returns the first one found.
     * <p/>
     * Preference is for {@literal logging.properties} or {@literal jboss-logging.properties}.
     *
     * @param file the file to check
     *
     * @return the configuration file if found, otherwise {@code null}
     *
     * @throws DeploymentUnitProcessingException if an error occurs.
     */
    private VirtualFile findConfigFile(final VirtualFile file) throws DeploymentUnitProcessingException {
        try {
            final List<VirtualFile> configFiles = file.getChildren(ConfigFilter.INSTANCE);
            for (final VirtualFile configFile : configFiles) {
                final String fileName = configFile.getName();
                if (DEFAULT_PROPERTIES.equals(fileName) || JBOSS_PROPERTIES.equals(fileName)) {
                    return configFile;
                }
            }
        } catch (IOException e) {
            throw LoggingLogger.ROOT_LOGGER.errorProcessingLoggingConfiguration(e);
        }
        return null;
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
    private LoggingConfigurationService configure(final ResourceRoot root, final VirtualFile configFile, final ClassLoader classLoader, final LogContext logContext) throws DeploymentUnitProcessingException {
        try (InputStream configStream = configFile.openStream()) {
            LoggingLogger.ROOT_LOGGER.debugf("Found logging configuration file: %s", configFile);
            // Create a properties file
            final Properties properties = new Properties();
            properties.load(new InputStreamReader(configStream, ENCODING));
            // Attempt to see if this is a J.U.L. configuration file
            if (isJulConfiguration(properties)) {
                LoggingLogger.ROOT_LOGGER.julConfigurationFileFound(configFile.getName());
            } else {
                // Configure the log context
                final ClassLoader current = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
                try {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
                    return new LoggingConfigurationService(LogContextConfiguration.create(logContext, properties), resolveRelativePath(root, configFile));
                } finally {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(current);
                }
            }
        } catch (Exception e) {
            throw LoggingLogger.ROOT_LOGGER.failedToConfigureLogging(e, configFile.getName());
        }
        return null;
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

    private static String resolveRelativePath(final ResourceRoot root, final VirtualFile configFile) {
        // Get the parent of the root resource so the deployment name will be included in the path
        final VirtualFile deployment = root.getRoot().getParent();
        if (deployment != null) {
            return configFile.getPathNameRelativeTo(deployment);
        }
        // This shouldn't be reached, but a fallback is always safe
        return configFile.getPathNameRelativeTo(root.getRoot());
    }

    private static class ConfigFilter implements VirtualFileFilter {

        static final ConfigFilter INSTANCE = new ConfigFilter();
        private final Set<String> configFiles = new HashSet<>(Arrays.asList(JBOSS_PROPERTIES, DEFAULT_PROPERTIES));

        @Override
        public boolean accepts(final VirtualFile file) {
            return configFiles.contains(file.getName());
        }
    }
}
