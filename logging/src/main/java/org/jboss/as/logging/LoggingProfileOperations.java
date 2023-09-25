/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationContext.ResultHandler;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.config.LogContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingProfileOperations {


    static class LoggingProfileAdd extends AbstractAddStepHandler {
        private final PathManager pathManager;

        LoggingProfileAdd(final PathManager pathManager) {
            this.pathManager = pathManager;
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
        protected void populateModel(final ModelNode operation, final ModelNode model) {
            model.setEmptyObject();
        }
    }

    static OperationStepHandler REMOVE_PROFILE = new AbstractRemoveStepHandler() {

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) {
            // Get the address and the name of the logger or handler
            final PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
            // Get the logging profile
            final String loggingProfile = getLoggingProfileName(address);
            final LoggingProfileContextSelector contextSelector = LoggingProfileContextSelector.getInstance();
            final LogContext logContext = contextSelector.get(loggingProfile);
            if (logContext != null) {
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(final OperationContext context, final ModelNode operation) {
                        final ConfigurationPersistence configuration = ConfigurationPersistence.getConfigurationPersistence(logContext);
                        if (configuration != null) {
                            final LogContextConfiguration logContextConfiguration = configuration.getLogContextConfiguration();
                            // Remove all loggers
                            for (String loggerName : logContextConfiguration.getLoggerNames()) {
                                logContextConfiguration.removeLoggerConfiguration(loggerName);
                            }
                            // Remove all the handlers
                            for (String handlerName : logContextConfiguration.getHandlerNames()) {
                                logContextConfiguration.removeHandlerConfiguration(handlerName);
                            }
                            // Remove all the filters
                            for (String filterName : logContextConfiguration.getFilterNames()) {
                                logContextConfiguration.removeFilterConfiguration(filterName);
                            }
                            // Remove all the formatters
                            for (String formatterName : logContextConfiguration.getFormatterNames()) {
                                logContextConfiguration.removeFormatterConfiguration(formatterName);
                            }
                            // Remove all the error managers
                            for (String errorManager : logContextConfiguration.getErrorManagerNames()) {
                                logContextConfiguration.removeErrorManagerConfiguration(errorManager);
                            }
                            // Add a commit step and don't use a rollback handler for this step
                            LoggingOperations.addCommitStep(context, configuration);
                            context.reloadRequired();
                        }
                        context.completeStep(new ResultHandler() {
                            @Override
                            public void handleResult(final ResultAction resultAction, final OperationContext context, final ModelNode operation) {
                                if (resultAction == ResultAction.KEEP) {
                                    contextSelector.remove(loggingProfile);
                                } else if (configuration != null) {
                                    context.revertReloadRequired();
                                }
                            }
                        });
                    }
                }, Stage.RUNTIME);
            }
        }

        @Override
        protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) {
        }
    };

    /**
     * Checks if the address is a logging profile address.
     *
     * @param address the address to check for the logging profile
     *
     * @return {@code true} if the address is a logging profile address, otherwise {@code false}
     */
    static boolean isLoggingProfileAddress(final PathAddress address) {
        return getLoggingProfileName(address) != null;
    }

    /**
     * Gets the logging profile name. If the address is not in a logging profile path, {@code null} is returned.
     *
     * @param address the address to check for the logging profile name
     *
     * @return the logging profile name or {@code null}
     */
    static String getLoggingProfileName(final PathAddress address) {
        for (PathElement pathElement : address) {
            if (CommonAttributes.LOGGING_PROFILE.equals(pathElement.getKey())) {
                return pathElement.getValue();
            }
        }
        return null;
    }
}
