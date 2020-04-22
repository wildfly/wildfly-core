/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.logging.loggers;

import static org.jboss.as.logging.CommonAttributes.FILTER;
import static org.jboss.as.logging.CommonAttributes.HANDLER_NAME;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.Logging.createOperationFailure;
import static org.jboss.as.logging.loggers.LoggerAttributes.FILTER_SPEC;
import static org.jboss.as.logging.loggers.LoggerAttributes.HANDLER;
import static org.jboss.as.logging.loggers.LoggerAttributes.HANDLERS;
import static org.jboss.as.logging.loggers.LoggerResourceDefinition.USE_PARENT_HANDLERS;
import static org.jboss.as.logging.loggers.RootLoggerResourceDefinition.RESOURCE_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.LoggingOperations;
import org.jboss.as.logging.filters.Filters;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;

/**
 * Date: 14.12.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class LoggerOperations {

    abstract static class LoggerUpdateOperationStepHandler extends LoggingOperations.LoggingUpdateOperationStepHandler {

        LoggerUpdateOperationStepHandler(final AttributeDefinition... attributes) {
            super(attributes);
        }

        @Override
        public final void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String loggerName = getLogManagerLoggerName(context.getCurrentAddressValue());
            LoggerConfiguration configuration = logContextConfiguration.getLoggerConfiguration(loggerName);
            if (configuration == null) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.loggerConfigurationNotFound(loggerName));
            }
            performRuntime(context, operation, configuration, loggerName, model);
        }

        /**
         * Executes additional processing for this step.
         *
         * @param context       the operation context
         * @param operation     the operation being executed
         * @param configuration the logging configuration
         * @param name          the name of the logger
         * @param model         the model to update
         *
         * @throws OperationFailedException if a processing error occurs
         */
        public abstract void performRuntime(OperationContext context, ModelNode operation, LoggerConfiguration configuration, String name, ModelNode model) throws OperationFailedException;
    }


    /**
     * A step handler for add operations of logging handlers. Adds default properties to the handler configuration.
     */
    static final class LoggerAddOperationStepHandler extends LoggingOperations.LoggingAddOperationStepHandler {
        private final AttributeDefinition[] attributes;

        LoggerAddOperationStepHandler(final AttributeDefinition[] attributes) {
            super(attributes);
            this.attributes = attributes;
        }

        @Override
        public void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
            for (AttributeDefinition attribute : attributes) {
                // Filter attribute needs to be converted to filter spec
                if (CommonAttributes.FILTER.equals(attribute)) {
                    final ModelNode filter = CommonAttributes.FILTER.validateOperation(operation);
                    if (filter.isDefined()) {
                        final String value = Filters.filterToFilterSpec(filter);
                        model.get(LoggerAttributes.FILTER_SPEC.getName()).set(value.isEmpty() ? new ModelNode() : new ModelNode(value));
                    }
                } else {
                    attribute.validateAndSet(operation, model);
                }
            }
        }

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final String loggerName = getLogManagerLoggerName(name);
            LoggerConfiguration configuration = logContextConfiguration.getLoggerConfiguration(loggerName);
            if (configuration == null) {
                LoggingLogger.ROOT_LOGGER.tracef("Adding logger '%s' at '%s'", name, context.getCurrentAddress());
                configuration = logContextConfiguration.addLoggerConfiguration(loggerName);
            }

            for (AttributeDefinition attribute : attributes) {
                handleProperty(attribute, context, model, configuration);
            }
        }
    }


    /**
     * A default log handler write attribute step handler.
     */
    static class LoggerWriteAttributeHandler extends LoggingOperations.LoggingWriteAttributeHandler {

        LoggerWriteAttributeHandler(final AttributeDefinition[] attributes) {
            super(attributes);
        }

        @Override
        protected boolean applyUpdate(final OperationContext context, final String attributeName, final String addressName, final ModelNode value, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String loggerName = getLogManagerLoggerName(addressName);
            if (logContextConfiguration.getLoggerNames().contains(loggerName)) {
                final LoggerConfiguration configuration = logContextConfiguration.getLoggerConfiguration(loggerName);
                if (LEVEL.getName().equals(attributeName)) {
                    handleProperty(LEVEL, context, value, configuration, false);
                } else if (FILTER.getName().equals(attributeName)) {
                    // Filter should be replaced by the filter-spec in the super class
                    handleProperty(FILTER_SPEC, context, value, configuration, false);
                } else if (FILTER_SPEC.getName().equals(attributeName)) {
                    handleProperty(FILTER_SPEC, context, value, configuration, false);
                } else if (HANDLERS.getName().equals(attributeName)) {
                    handleProperty(HANDLERS, context, value, configuration, false);
                } else if (USE_PARENT_HANDLERS.getName().equals(attributeName)) {
                    handleProperty(USE_PARENT_HANDLERS, context, value, configuration, false);
                }
            }
            return false;
        }

        @Override
        protected void finishModelStage(final OperationContext context, final ModelNode operation, final String attributeName,
                                        final ModelNode newValue, final ModelNode oldValue, final Resource model) throws OperationFailedException {
            super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);
            // If a filter attribute, update the filter-spec attribute
            if (CommonAttributes.FILTER.getName().equals(attributeName)) {
                final String filterSpec = Filters.filterToFilterSpec(newValue);
                final ModelNode filterSpecValue = (filterSpec.isEmpty() ? new ModelNode() : new ModelNode(filterSpec));
                // Undefine the filter-spec
                model.getModel().get(LoggerAttributes.FILTER_SPEC.getName()).set(filterSpecValue);
            }
        }
    }

    /**
     * A step handler to remove a logger
     */
    static final OperationStepHandler REMOVE_LOGGER = new LoggingOperations.LoggingRemoveOperationStepHandler() {

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            // Disable the logger before removing it
            final String loggerName = getLogManagerLoggerName(context.getCurrentAddressValue());
            final LoggerConfiguration configuration = logContextConfiguration.getLoggerConfiguration(loggerName);
            if (configuration == null) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.loggerNotFound(loggerName));
            }
            logContextConfiguration.removeLoggerConfiguration(loggerName);
        }
    };

    /**
     * A step handler to add a handler.
     */
    static final OperationStepHandler ADD_HANDLER = new LoggerUpdateOperationStepHandler(HANDLERS) {

        @Override
        public void updateModel(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            final ModelNode handlerName = operation.get(HANDLER_NAME.getName());
            // Get the current handlers, add the handler and set the model value
            final ModelNode handlers = model.get(HANDLERS.getName()).clone();
            if (!handlers.isDefined()) {
                handlers.setEmptyList();
            }
            handlers.add(handlerName);
            HANDLERS.getValidator().validateParameter(HANDLERS.getName(), handlers);
            model.get(HANDLERS.getName()).add(handlerName);
            HANDLER.addCapabilityRequirements(context, resource, handlerName);
        }

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final LoggerConfiguration configuration, final String name, final ModelNode model) throws OperationFailedException {
            // Get the handler name, uses the operation to get the single handler name being added
            final String handlerName = HANDLER_NAME.resolveModelAttribute(context, operation).asString();
            final String loggerName = getLogManagerLoggerName(name);
            if (configuration.getHandlerNames().contains(handlerName)) {
                LoggingLogger.ROOT_LOGGER.tracef("Handler %s is already assigned to logger %s", handlerName, loggerName);
            } else {
                LoggingLogger.ROOT_LOGGER.tracef("Adding handler '%s' to logger '%s' at '%s'", handlerName, getLogManagerLoggerName(loggerName), context.getCurrentAddress());
                configuration.addHandlerName(handlerName);
            }
        }
    };

    /**
     * A step handler to remove a handler.
     */
    static final OperationStepHandler REMOVE_HANDLER = new LoggerUpdateOperationStepHandler(HANDLERS) {

        @Override
        public void updateModel(final OperationContext context, final ModelNode operation, final ModelNode model) {
            final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            final String handlerName = operation.get(HANDLER_NAME.getName()).asString();
            // Create a new handler list for the model
            boolean found = false;
            final List<ModelNode> handlers = model.get(HANDLERS.getName()).asList();
            final List<ModelNode> newHandlers = new ArrayList<>(handlers.size());
            for (ModelNode handler : handlers) {
                if (handlerName.equals(handler.asString())) {
                    HANDLER.removeCapabilityRequirements(context, resource, handler);
                    found = true;
                } else {
                    newHandlers.add(handler);
                }
            }
            if (found) {
                model.get(HANDLERS.getName()).set(newHandlers);
            }
        }

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final LoggerConfiguration configuration, final String name, final ModelNode model) throws OperationFailedException {
            // Uses the operation to get the single handler name being added
            configuration.removeHandlerName(HANDLER_NAME.resolveModelAttribute(context, operation).asString());
        }
    };

    /**
     * A step handler to remove a handler.
     */
    static final OperationStepHandler CHANGE_LEVEL = new LoggerUpdateOperationStepHandler(LEVEL) {

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final LoggerConfiguration configuration, final String name, final ModelNode model) throws OperationFailedException {
            handleProperty(LEVEL, context, model, configuration);
        }
    };

    private static void handleProperty(final AttributeDefinition attribute, final OperationContext context, final ModelNode model,
                                       final LoggerConfiguration configuration) throws OperationFailedException {
        handleProperty(attribute, context, model, configuration, true);
    }

    private static void handleProperty(final AttributeDefinition attribute, final OperationContext context, final ModelNode model,
                                       final LoggerConfiguration configuration, final boolean resolveValue) throws OperationFailedException {
        if (FILTER_SPEC.equals(attribute)) {
            final ModelNode valueNode = (resolveValue ? FILTER_SPEC.resolveModelAttribute(context, model) : model);
            final String resolvedValue = (valueNode.isDefined() ? valueNode.asString() : null);
            configuration.setFilter(resolvedValue);
        } else if (LEVEL.equals(attribute)) {
            final String resolvedValue = (resolveValue ? LEVEL.resolvePropertyValue(context, model) : LEVEL.resolver().resolveValue(context, model));
            configuration.setLevel(resolvedValue);
        } else if (HANDLERS.equals(attribute)) {
            final Collection<String> resolvedValue = (resolveValue ? HANDLERS.resolvePropertyValue(context, model) : HANDLERS.resolver().resolveValue(context, model));
            configuration.setHandlerNames(resolvedValue);
        } else if (USE_PARENT_HANDLERS.equals(attribute)) {
            final ModelNode useParentHandlers = (resolveValue ? USE_PARENT_HANDLERS.resolveModelAttribute(context, model) : model);
            final Boolean resolvedValue = (useParentHandlers.isDefined() ? useParentHandlers.asBoolean() : null);
            configuration.setUseParentHandlers(resolvedValue);
        }
    }

    /**
     * Returns the logger name that should be used in the log manager.
     *
     * @param name the name of the logger from the resource
     *
     * @return the name of the logger
     */
    private static String getLogManagerLoggerName(final String name) {
        return (name.equals(RESOURCE_NAME) ? CommonAttributes.ROOT_LOGGER_NAME : name);
    }
}
