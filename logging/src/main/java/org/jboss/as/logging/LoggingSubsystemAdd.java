/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.logging.deployments.LoggingCleanupDeploymentProcessor;
import org.jboss.as.logging.deployments.LoggingConfigDeploymentProcessor;
import org.jboss.as.logging.deployments.LoggingDependencyDeploymentProcessor;
import org.jboss.as.logging.deployments.LoggingDeploymentResourceProcessor;
import org.jboss.as.logging.deployments.LoggingProfileDeploymentProcessor;
import org.jboss.as.logging.formatters.CustomFormatterResourceDefinition;
import org.jboss.as.logging.formatters.JsonFormatterResourceDefinition;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition;
import org.jboss.as.logging.handlers.ConsoleHandlerResourceDefinition;
import org.jboss.as.logging.handlers.CustomHandlerResourceDefinition;
import org.jboss.as.logging.handlers.FileHandlerResourceDefinition;
import org.jboss.as.logging.handlers.PeriodicHandlerResourceDefinition;
import org.jboss.as.logging.handlers.PeriodicSizeRotatingHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SocketHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition;
import org.jboss.as.logging.loggers.LoggerResourceDefinition;
import org.jboss.as.logging.loggers.RootLoggerResourceDefinition;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.as.logging.logmanager.WildFlyLogContextSelector;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.config.LogContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingSubsystemAdd extends AbstractAddStepHandler {

    private final PathManager pathManager;
    private final WildFlyLogContextSelector contextSelector;

    LoggingSubsystemAdd(final PathManager pathManager, final WildFlyLogContextSelector contextSelector) {
        super(LoggingResourceDefinition.ATTRIBUTES);
        this.pathManager = pathManager;
        this.contextSelector = contextSelector;
    }

    @Override
    protected Resource createResource(final OperationContext context) {
        if (pathManager == null) {
            return super.createResource(context);
        }
        final Resource resource = new LoggingResource(pathManager);
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);
        return resource;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        final boolean addDependencies = LoggingResourceDefinition.ADD_LOGGING_API_DEPENDENCIES.resolveModelAttribute(context, model).asBoolean();
        final boolean useLoggingConfig = LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG.resolveModelAttribute(context, model).asBoolean();
        context.addStep(new AbstractDeploymentChainStep() {
            @Override
            protected void execute(final DeploymentProcessorTarget processorTarget) {
                processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_LOGGING_CLEANUP, new LoggingCleanupDeploymentProcessor());
                if (addDependencies) {
                    processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME, Phase.DEPENDENCIES, Phase.DEPENDENCIES_LOGGING, new LoggingDependencyDeploymentProcessor());
                }
                processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, Phase.POST_MODULE_LOGGING_CONFIG,
                        new LoggingConfigDeploymentProcessor(contextSelector, LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG.getName(), useLoggingConfig));
                processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME, Phase.POST_MODULE, Phase.POST_MODULE_LOGGING_PROFILE, new LoggingProfileDeploymentProcessor(contextSelector));
                processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME, Phase.INSTALL, Phase.INSTALL_LOGGING_DEPLOYMENT_RESOURCES,
                        new LoggingDeploymentResourceProcessor());
            }
        }, Stage.RUNTIME);

        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);

        final ConfigurationPersistence configurationPersistence = ConfigurationPersistence.getOrCreateConfigurationPersistence();
        final LogContextConfiguration logContextConfiguration = configurationPersistence.getLogContextConfiguration();
        // root logger
        if (!resource.hasChild(RootLoggerResourceDefinition.ROOT_LOGGER_PATH)) {
            LoggingLogger.ROOT_LOGGER.tracef("Removing the root logger configuration.");
            logContextConfiguration.removeLoggerConfiguration(CommonAttributes.ROOT_LOGGER_NAME);
        }

        // remove all configured loggers which aren't in the model
        if (resource.hasChild(PathElement.pathElement(LoggerResourceDefinition.NAME))) {
            final Set<String> loggerNames = resource.getChildrenNames(LoggerResourceDefinition.NAME);
            final List<String> configuredLoggerNames = logContextConfiguration.getLoggerNames();
            // Always remove the root
            configuredLoggerNames.remove(CommonAttributes.ROOT_LOGGER_NAME);
            configuredLoggerNames.removeAll(loggerNames);
            for (String name : configuredLoggerNames) {
                LoggingLogger.ROOT_LOGGER.tracef("Removing logger configuration for '%s'", name);
                logContextConfiguration.removeLoggerConfiguration(name);
            }
        }

        // Create a collection of all subsystem handlers
        final Collection<String> subsystemHandlers = new ArrayList<>();
        subsystemHandlers.addAll(resource.getChildrenNames(AsyncHandlerResourceDefinition.NAME));
        subsystemHandlers.addAll(resource.getChildrenNames(ConsoleHandlerResourceDefinition.NAME));
        subsystemHandlers.addAll(resource.getChildrenNames(CustomHandlerResourceDefinition.NAME));
        subsystemHandlers.addAll(resource.getChildrenNames(FileHandlerResourceDefinition.NAME));
        subsystemHandlers.addAll(resource.getChildrenNames(PeriodicHandlerResourceDefinition.NAME));
        subsystemHandlers.addAll(resource.getChildrenNames(PeriodicSizeRotatingHandlerResourceDefinition.NAME));
        subsystemHandlers.addAll(resource.getChildrenNames(SizeRotatingHandlerResourceDefinition.NAME));
        subsystemHandlers.addAll(resource.getChildrenNames(SocketHandlerResourceDefinition.NAME));
        subsystemHandlers.addAll(resource.getChildrenNames(SyslogHandlerResourceDefinition.NAME));

        // handlers
        final List<String> configuredHandlerNames = logContextConfiguration.getHandlerNames();
        configuredHandlerNames.removeAll(subsystemHandlers);
        for (String name : configuredHandlerNames) {
            LoggingLogger.ROOT_LOGGER.tracef("Removing handler configuration for '%s'", name);
            // Clean up any possible POJO references
            logContextConfiguration.removePojoConfiguration(name);
            // Remove the handler configuration
            logContextConfiguration.removeHandlerConfiguration(name);
        }

        // Remove formatters
        final List<String> configuredFormatters = logContextConfiguration.getFormatterNames();
        configuredFormatters.removeAll(resource.getChildrenNames(PatternFormatterResourceDefinition.NAME));
        configuredFormatters.removeAll(resource.getChildrenNames(CustomFormatterResourceDefinition.NAME));
        configuredFormatters.removeAll(resource.getChildrenNames(JsonFormatterResourceDefinition.NAME));
        // Formatter names could also be the name of a handler if the formatter attribute is used rather than a named-formatter
        configuredFormatters.removeAll(subsystemHandlers);

        for (String name : configuredFormatters) {
            LoggingLogger.ROOT_LOGGER.tracef("Removing formatter configuration for '%s'", name);
            // Remove the formatter configuration
            logContextConfiguration.removeFormatterConfiguration(name);
        }

        LoggingOperations.addCommitStep(context, configurationPersistence);
        LoggingLogger.ROOT_LOGGER.trace("Logging subsystem has been added.");
    }
}
