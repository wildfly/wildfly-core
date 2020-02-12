/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.AttachmentKey;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationContext.ResultHandler;
import org.jboss.as.controller.OperationContext.RollbackHandler;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.logging.filters.Filters;
import org.jboss.as.logging.loggers.LoggerAttributes;
import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public final class LoggingOperations {

    /**
     * Adds a {@link Stage#RUNTIME runtime} step to the context that will commit or rollback any logging changes. Also
     * if not a logging profile writes the {@code logging.properties} file.
     * <p>
     * Note the commit step will only be added if process type is a
     * {@linkplain org.jboss.as.controller.ProcessType#isServer() server}.
     * </p>
     *
     * @param context                  the context to add the step to
     * @param configurationPersistence the configuration to commit
     */
    static void addCommitStep(final OperationContext context, final ConfigurationPersistence configurationPersistence) {
        // This should only check that it's a server for the commit step. The logging.properties may need to be written
        // in ADMIN_ONLY mode
        if (context.getProcessType().isServer()) {
            context.addStep(new CommitOperationStepHandler(configurationPersistence), Stage.RUNTIME);
        }
    }

    private static ConfigurationPersistence getOrCreateConfigurationPersistence(final OperationContext context) {
        final PathAddress address = context.getCurrentAddress();
        final ConfigurationPersistence configurationPersistence;
        if (LoggingProfileOperations.isLoggingProfileAddress(address)) {
            final LogContext logContext = LoggingProfileContextSelector.getInstance().getOrCreate(LoggingProfileOperations.getLoggingProfileName(address));
            configurationPersistence = ConfigurationPersistence.getOrCreateConfigurationPersistence(logContext);
        } else {
            configurationPersistence = ConfigurationPersistence.getOrCreateConfigurationPersistence();
        }
        return configurationPersistence;
    }

    private static ConfigurationPersistence getConfigurationPersistence(final OperationContext context) {
        final PathAddress address = context.getCurrentAddress();
        final LogContext logContext;
        if (LoggingProfileOperations.isLoggingProfileAddress(address)) {
            logContext = LoggingProfileContextSelector.getInstance().get(LoggingProfileOperations.getLoggingProfileName(address));
        } else {
            logContext = LogContext.getLogContext();
        }
        return ConfigurationPersistence.getConfigurationPersistence(logContext);
    }

    private static final class CommitOperationStepHandler implements OperationStepHandler {
        private static final AttachmentKey<Boolean> WRITTEN_KEY = AttachmentKey.create(Boolean.class);
        private final ConfigurationPersistence configurationPersistence;
        private final boolean persistConfig;

        @SuppressWarnings("deprecation")
        CommitOperationStepHandler(final ConfigurationPersistence configurationPersistence) {
            this.configurationPersistence = configurationPersistence;
            persistConfig = Boolean.parseBoolean(WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.JBOSS_PERSIST_SERVER_CONFIG, Boolean.toString(true)));
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            configurationPersistence.prepare();
            context.completeStep(new ResultHandler() {
                @Override
                public void handleResult(final ResultAction resultAction, final OperationContext context, final ModelNode operation) {
                    if (resultAction == ResultAction.KEEP) {
                        configurationPersistence.commit();
                        if (!LoggingProfileOperations.isLoggingProfileAddress(context.getCurrentAddress())) {
                            // Write once
                            if (context.getAttachment(WRITTEN_KEY) == null) {
                                context.attachIfAbsent(WRITTEN_KEY, Boolean.TRUE);
                                if (persistConfig) {
                                    configurationPersistence.writeConfiguration(context);
                                }
                            }
                        }
                    } else if (resultAction == ResultAction.ROLLBACK) {
                        configurationPersistence.rollback();
                    }
                }
            });
        }
    }

    public static class ReadFilterOperationStepHandler implements OperationStepHandler {

        public static final ReadFilterOperationStepHandler INSTANCE = new ReadFilterOperationStepHandler();

        private ReadFilterOperationStepHandler() {

        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
            final ModelNode filter = LoggerAttributes.FILTER_SPEC.resolveModelAttribute(context, model);
            if (filter.isDefined()) {
                context.getResult().set(Filters.filterSpecToFilter(filter.asString()));
            }
        }
    }


    /**
     * A base step handler for logging operations.
     */
    public abstract static class LoggingAddOperationStepHandler extends AbstractAddStepHandler {
        public LoggingAddOperationStepHandler(final AttributeDefinition... attributes) {
            super(attributes);
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            super.execute(context, operation);
            context.completeStep(new RollbackHandler() {
                @Override
                public void handleRollback(final OperationContext context, final ModelNode operation) {
                    final ConfigurationPersistence configurationPersistence = getConfigurationPersistence(context);
                    if (configurationPersistence != null) {
                        configurationPersistence.rollback();
                    }
                }
            });
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
            super.populateModel(context, operation, resource);
            final ConfigurationPersistence configurationPersistence = getOrCreateConfigurationPersistence(context);
            final OperationStepHandler additionalStep = additionalModelStep(configurationPersistence);
            if (additionalStep != null) {
                context.addStep(additionalStep, Stage.MODEL);
            }
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final ConfigurationPersistence configurationPersistence = getOrCreateConfigurationPersistence(context);
            final LogContextConfiguration logContextConfiguration = configurationPersistence.getLogContextConfiguration();

            performRuntime(context, operation, model, logContextConfiguration);
            addCommitStep(context, configurationPersistence);
            final OperationStepHandler afterCommit = afterCommit(logContextConfiguration, model);
            if (afterCommit != null) {
                context.addStep(afterCommit, Stage.RUNTIME);
            }
        }

        /**
         * An {@link OperationStepHandler} to register after the commit step has been registered.
         *
         * @param logContextConfiguration the log context configuration used
         * @param model                   the current model to use
         *
         * @return an operation step handler to register or {@code null} to not register a step after the commit step
         */
        protected OperationStepHandler afterCommit(final LogContextConfiguration logContextConfiguration, final ModelNode model) {
            return null;
        }

        /**
         * An {@link OperationStepHandler} that will be registered after the {@link #populateModel(OperationContext, ModelNode, Resource)}
         * is executed in the {@linkplain Stage#MODEL model stage}. If this method returns {@code null} the step will
         * not be registered.
         *
         * @param logContextConfiguration the log context configuration to use
         *
         * @return an operation step handler to register or {@code null} to not register a step after the commit step
         */
        protected OperationStepHandler additionalModelStep(final LogContextConfiguration logContextConfiguration) {
            return null;
        }

        /**
         * Executes additional processing for this step.
         *
         * @param context                 the operation context
         * @param operation               the operation being executed
         * @param model                   the model to update
         * @param logContextConfiguration the logging context configuration
         *
         * @throws OperationFailedException if a processing error occurs
         */
        public abstract void performRuntime(OperationContext context, ModelNode operation, ModelNode model, LogContextConfiguration logContextConfiguration) throws OperationFailedException;

    }


    /**
     * A base update step handler for logging operations.
     */
    public abstract static class LoggingUpdateOperationStepHandler implements OperationStepHandler {
        private final AttributeDefinition[] attributes;

        protected LoggingUpdateOperationStepHandler(final AttributeDefinition... attributes) {
            this.attributes = attributes;
        }

        @Override
        public final void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final ConfigurationPersistence configurationPersistence = getOrCreateConfigurationPersistence(context);
            final LogContextConfiguration logContextConfiguration = configurationPersistence.getLogContextConfiguration();

            final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            final ModelNode model = resource.getModel();
            updateModel(context, operation, model);
            if (context.isNormalServer()) {
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                        performRuntime(context, operation, model, logContextConfiguration);
                    }
                }, Stage.RUNTIME);
            }
            addCommitStep(context, configurationPersistence);
            context.completeStep(new RollbackHandler() {
                @Override
                public void handleRollback(final OperationContext context, final ModelNode operation) {
                    configurationPersistence.rollback();
                }
            });
        }

        /**
         * Updates the model based on the operation.
         *
         * @param context   the operation context
         * @param operation the operation being executed
         * @param model     the model to update
         *
         * @throws OperationFailedException if a processing error occurs
         */
        public void updateModel(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            final ModelNode submodel = resource.getModel();
            for (AttributeDefinition attribute : attributes) {
                final String attributeName = attribute.getName();
                final ModelNode currentValue = submodel.get(attributeName).clone();
                // Filter attribute needs to be converted to filter spec
                if (CommonAttributes.FILTER.equals(attribute)) {
                    final ModelNode filter = CommonAttributes.FILTER.validateOperation(operation);
                    if (filter.isDefined()) {
                        final String value = Filters.filterToFilterSpec(filter);
                        model.get(LoggerAttributes.FILTER_SPEC.getName()).set(value.isEmpty() ? new ModelNode() : new ModelNode(value));
                    }
                } else {
                    // Only update the model for attributes that are defined in the operation
                    if (operation.has(attribute.getName())) {
                        attribute.validateAndSet(operation, model);
                    }
                }
                final ModelNode newValue = model.get(attributeName).clone();
                recordCapabilitiesAndRequirements(context, resource, attribute, newValue, currentValue);
            }
        }

        /**
         * Executes additional processing for this step.
         *
         * @param context                 the operation context
         * @param operation               the operation being executed
         * @param model                   the model to update
         * @param logContextConfiguration the logging context configuration
         *
         * @throws OperationFailedException if a processing error occurs
         */
        public abstract void performRuntime(OperationContext context, ModelNode operation, ModelNode model, LogContextConfiguration logContextConfiguration) throws OperationFailedException;

        protected AttributeDefinition[] getAttributes() {
            return attributes;
        }


        protected void recordCapabilitiesAndRequirements(final OperationContext context, final Resource resource,
                                                         final AttributeDefinition attributeDefinition, final ModelNode newValue,
                                                         final ModelNode oldValue) {

            attributeDefinition.removeCapabilityRequirements(context, resource, oldValue);
            attributeDefinition.addCapabilityRequirements(context, resource, newValue);
        }
    }


    /**
     * A base remove step handler for logging operations.
     */
    public abstract static class LoggingRemoveOperationStepHandler extends AbstractRemoveStepHandler {

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final ConfigurationPersistence configurationPersistence = getOrCreateConfigurationPersistence(context);
            final LogContextConfiguration logContextConfiguration = configurationPersistence.getLogContextConfiguration();

            performRuntime(context, operation, model, logContextConfiguration);
            addCommitStep(context, configurationPersistence);
        }

        @Override
        protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final ConfigurationPersistence configurationPersistence = getConfigurationPersistence(context);
            if (configurationPersistence != null) {
                configurationPersistence.rollback();
                revertRuntime(context, operation, model, configurationPersistence);
            }
        }

        /**
         * Revert any runtime changes.
         *
         * @param context                 the operation context
         * @param operation               the operation being executed
         * @param model                   the model to update
         * @param logContextConfiguration the logging context configuration
         *
         * @throws OperationFailedException if the revert fails
         */
        protected void revertRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            // Do nothing by default
        }

        /**
         * Perform runtime steps.
         *
         * @param context                 the operation context
         * @param operation               the operation being executed
         * @param model                   the model to update
         * @param logContextConfiguration the logging context configuration
         *
         * @throws OperationFailedException if the remove fails
         */
        protected abstract void performRuntime(OperationContext context, ModelNode operation, ModelNode model, LogContextConfiguration logContextConfiguration) throws OperationFailedException;

    }


    /**
     * A default log handler write attribute step handler.
     */
    public abstract static class LoggingWriteAttributeHandler extends AbstractWriteAttributeHandler<ConfigurationPersistence> {
        private final AttributeDefinition[] attributes;

        protected LoggingWriteAttributeHandler(final AttributeDefinition[] attributes) {
            super(attributes);
            this.attributes = attributes;
        }

        @Override
        protected final boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<ConfigurationPersistence> handbackHolder) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final ConfigurationPersistence configurationPersistence = getOrCreateConfigurationPersistence(context);
            final LogContextConfiguration logContextConfiguration = configurationPersistence.getLogContextConfiguration();
            handbackHolder.setHandback(configurationPersistence);
            final boolean restartRequired = applyUpdate(context, attributeName, name, resolvedValue, logContextConfiguration);
            addCommitStep(context, configurationPersistence);
            final OperationStepHandler afterCommit = afterCommit(logContextConfiguration, attributeName, resolvedValue, currentValue);
            if (afterCommit != null && !restartRequired) {
                context.addStep(afterCommit, Stage.RUNTIME);
            }
            return restartRequired;
        }

        /**
         * An {@link OperationStepHandler} to register after the commit step has been registered.
         * <p>
         * If {@linkplain #applyUpdateToRuntime(OperationContext, ModelNode, String, ModelNode, ModelNode, HandbackHolder)}
         * returns {@code true} this step will not be registered.
         * </p>
         *
         * @param logContextConfiguration the log context configuration used
         * @param attributeName           the name of the attribute being written
         * @param currentValue            the current value of the attribute
         * @param resolvedValue           the resolved value of the attribute
         *
         * @return an operation step handler to register or {@code null} to not register a step after the commit step
         */
        protected OperationStepHandler afterCommit(final LogContextConfiguration logContextConfiguration,
                                                   final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue) {
            return null;
        }

        /**
         * Applies the update to the runtime.
         *
         * @param context                 the operation context
         * @param attributeName           the name of the attribute being written
         * @param addressName             the name of the handler or logger
         * @param value                   the value to set the attribute to
         * @param logContextConfiguration the log context configuration
         *
         * @return {@code true} if a restart is required, otherwise {@code false}
         *
         * @throws OperationFailedException if an error occurs
         */
        protected abstract boolean applyUpdate(final OperationContext context, final String attributeName, final String addressName, final ModelNode value, final LogContextConfiguration logContextConfiguration) throws OperationFailedException;

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final ConfigurationPersistence configurationPersistence) throws OperationFailedException {
            final LogContextConfiguration logContextConfiguration = configurationPersistence.getLogContextConfiguration();
            // First forget the configuration
            logContextConfiguration.forget();
        }

        @Override
        protected void validateUpdatedModel(final OperationContext context, final Resource model) throws OperationFailedException {
            final ModelNode submodel = model.getModel();
            if (submodel.hasDefined(CommonAttributes.FILTER.getName())) {
                final String filterSpec = Filters.filterToFilterSpec(CommonAttributes.FILTER.resolveModelAttribute(context, submodel));
                submodel.remove(CommonAttributes.FILTER.getName());
                submodel.get(LoggerAttributes.FILTER_SPEC.getName()).set(filterSpec.isEmpty() ? new ModelNode() : new ModelNode(filterSpec));
            }
        }

        /**
         * Returns a collection of attributes used for the write attribute.
         *
         * @return a collection of attributes
         */
        public final AttributeDefinition[] getAttributes() {
            return attributes;
        }
    }
}
