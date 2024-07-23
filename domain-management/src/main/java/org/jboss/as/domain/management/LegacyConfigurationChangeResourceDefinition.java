/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURATION_CHANGES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.ConfigurationChangesCollector;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.Service;

/**
 * Resource to list all configuration changes.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class LegacyConfigurationChangeResourceDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition MAX_HISTORY = SimpleAttributeDefinitionBuilder.create(
            ModelDescriptionConstants.MAX_HISTORY, ModelType.INT, true)
            .setDefaultValue(new ModelNode(10))
            .build();
    public static final PathElement PATH = PathElement.pathElement(SERVICE, CONFIGURATION_CHANGES);
    public static final String OPERATION_NAME = "list-changes";
    static final String CONFIGURATION_CHANGES_CAPABILITY_NAME = "org.wildfly.management.configuration.changes";
    public static final RuntimeCapability<Void> CONFIGURATION_CHANGES_CAPABILITY = RuntimeCapability.Builder
            .of(CONFIGURATION_CHANGES_CAPABILITY_NAME, false, Void.class)
            .build();

    public static final LegacyConfigurationChangeResourceDefinition INSTANCE = new LegacyConfigurationChangeResourceDefinition();

    static ResourceDefinition forDomain() {
        return new SimpleResourceDefinition(new Parameters(PATH, DomainManagementResolver.getResolver(CORE, MANAGEMENT, SERVICE, CONFIGURATION_CHANGES))
                .setAddHandler(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        String warning = DomainManagementLogger.ROOT_LOGGER.removedBrokenResource(context.getCurrentAddress().toCLIStyleString());
                        DomainManagementLogger.ROOT_LOGGER.warn(warning);
                        context.getResult().add(warning);
                    }

                })
                .setDeprecatedSince(ModelVersion.create(4, 2)));
    }

    private LegacyConfigurationChangeResourceDefinition() {
        super(new Parameters(PATH, DomainManagementResolver.getResolver(CORE, MANAGEMENT, SERVICE, CONFIGURATION_CHANGES))
                .addCapabilities(CONFIGURATION_CHANGES_CAPABILITY)
                .setAddHandler(new LegacyConfigurationChangeResourceAddHandler())
                .setRemoveHandler(new LegacyConfigurationChangeResourceRemoveHandler()));
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(LegacyConfigurationChangesHandler.DEFINITION, LegacyConfigurationChangesHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(MAX_HISTORY, null, new LegacyMaxHistoryWriteHandler(ConfigurationChangesCollector.INSTANCE));
    }

    private static class LegacyConfigurationChangeResourceAddHandler extends AbstractAddStepHandler {

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            ModelNode maxHistory = MAX_HISTORY.resolveModelAttribute(context, model);
            context.getServiceTarget().addService(CONFIGURATION_CHANGES_CAPABILITY.getCapabilityServiceName()).setInstance(Service.NULL).install();
            ConfigurationChangesCollector.INSTANCE.setMaxHistory(maxHistory.asInt());
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER
                    && (context.getProcessType() != ProcessType.EMBEDDED_SERVER
                    && context.getRunningMode() != RunningMode.ADMIN_ONLY);
        }

    }

    private static class LegacyConfigurationChangeResourceRemoveHandler extends AbstractRemoveStepHandler {

        private LegacyConfigurationChangeResourceRemoveHandler() {
            super();
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER
                    && (context.getProcessType() != ProcessType.EMBEDDED_SERVER
                    && context.getRunningMode() != RunningMode.ADMIN_ONLY);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
            ConfigurationChangesCollector.INSTANCE.deactivate();
            context.removeService(CONFIGURATION_CHANGES_CAPABILITY.getCapabilityServiceName());
        }
    }

    private static class LegacyMaxHistoryWriteHandler extends AbstractWriteAttributeHandler<Integer> {

        private final ConfigurationChangesCollector collector;

        private LegacyMaxHistoryWriteHandler(ConfigurationChangesCollector collector) {
            this.collector = collector;
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Integer> handbackHolder) throws OperationFailedException {
            collector.setMaxHistory(resolvedValue.asInt());
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Integer handback) throws OperationFailedException {
            collector.setMaxHistory(valueToRestore.asInt());
        }
    }

    private static class LegacyConfigurationChangesHandler extends AbstractRuntimeOnlyHandler {

        private static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME,
                DomainManagementResolver.getResolver(CORE, MANAGEMENT, SERVICE, CONFIGURATION_CHANGES))
                .setReplyType(ModelType.STRING)
                .setRuntimeOnly()
                .build();
        private static final LegacyConfigurationChangesHandler INSTANCE = new LegacyConfigurationChangesHandler(ConfigurationChangesCollector.INSTANCE);
        private static final Set<Action.ActionEffect> ADDRESS_EFFECT = EnumSet.of(Action.ActionEffect.ADDRESS);
        private static final Set<Action.ActionEffect> READ_EFFECT = EnumSet.of(Action.ActionEffect.READ_CONFIG, Action.ActionEffect.READ_RUNTIME);

        private final ConfigurationChangesCollector collector;

        private LegacyConfigurationChangesHandler(ConfigurationChangesCollector collector) {
            this.collector = collector;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (collector != null) {
                ModelNode result = context.getResult().setEmptyList();
                for (ModelNode change : collector.getChanges()) {
                    ModelNode configurationChange = change.clone();
                    secureHistory(context, configurationChange);
                    result.add(configurationChange);
                }
            }
        }

        /**
         * Checks if the calling user may execute the operation. If he can then he cvan see it in the result.
         *
         * @param context
         * @param configurationChange
         * @throws OperationFailedException
         */
        private void secureHistory(OperationContext context, ModelNode configurationChange) throws OperationFailedException {
            if (configurationChange.has(OPERATIONS)) {
                List<ModelNode> operations = configurationChange.get(OPERATIONS).asList();
                ModelNode authorizedOperations = configurationChange.get(OPERATIONS).setEmptyList();
                for (ModelNode operation : operations) {
                    authorizedOperations.add(secureOperation(context, operation));
                }
            }
        }

        /**
         * Secure the operation : - if the caller can address the resource we check if he can see the operation
         * parameters. - otherwise we return the operation without its address and parameters.
         *
         * @param context the operation context.
         * @param operation the operation we are securing.
         * @return the secured opreation aka trimmed of all sensitive data.
         * @throws OperationFailedException
         */
        private ModelNode secureOperation(OperationContext context, ModelNode operation) throws OperationFailedException {
            PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
            for (int i = 0; i < address.size(); i++) {
                if (!isAccessPermitted(context, address.subAddress(0, i).toModelNode())) {
                    return accessDenied(operation);
                }
            }
            ModelNode fakeOperation = new ModelNode();
            fakeOperation.get(OP).set(READ_RESOURCE_OPERATION);
            fakeOperation.get(OP_ADDR).set(address.toModelNode());
            AuthorizationResult authResult = context.authorize(fakeOperation, ADDRESS_EFFECT);
            if (authResult.getDecision() == AuthorizationResult.Decision.PERMIT) {
                return secureOperationParameters(context, operation);
            }
            return accessDenied(operation);
        }

        private ModelNode accessDenied(ModelNode operation) {
            ModelNode securedOperation = new ModelNode();
            securedOperation.get(OP).set(operation.get(OP));
            securedOperation.get(OP_ADDR).set(ControllerLogger.MGMT_OP_LOGGER.permissionDenied());
            return securedOperation;
        }
        private boolean isAccessPermitted(OperationContext context, ModelNode address) {
            ModelNode fakeOperation = new ModelNode();
            fakeOperation.get(OP).set(READ_RESOURCE_OPERATION);
            fakeOperation.get(OP_ADDR).set(address);
            AuthorizationResult authResult = context.authorize(fakeOperation, READ_EFFECT);
            return (authResult.getDecision() == AuthorizationResult.Decision.PERMIT);
        }

        /**
         * Checks if the calling user may execute the operation. If he may then he can see it te full operation
         * parameters.
         *
         * @param context the operation context.
         * @param op the operation we are securing.
         * @return the secured operation.
         * @throws OperationFailedException
         */
        private ModelNode secureOperationParameters(OperationContext context, ModelNode op) throws OperationFailedException {
            ModelNode operation = op.clone();
            OperationEntry operationEntry = context.getRootResourceRegistration().getOperationEntry(
                    PathAddress.pathAddress(operation.get(OP_ADDR)), operation.get(OP).asString());
            Set<Action.ActionEffect> effects = getEffects(operationEntry);
            if (context.authorize(operation, effects).getDecision() == AuthorizationResult.Decision.PERMIT) {
                return operation;
            } else {
                ModelNode securedOperation = new ModelNode();
                securedOperation.get(OP).set(operation.get(OP));
                securedOperation.get(OP_ADDR).set(operation.get(OP_ADDR));
                return securedOperation;
            }
        }

        private Set<Action.ActionEffect> getEffects(OperationEntry operationEntry) {
            Set<Action.ActionEffect> effects = new HashSet<>(5);
            effects.add(Action.ActionEffect.ADDRESS);
            if (operationEntry != null) {
                effects.add(Action.ActionEffect.READ_RUNTIME);
                if (!operationEntry.getFlags().contains(OperationEntry.Flag.RUNTIME_ONLY)) {
                    effects.add(Action.ActionEffect.READ_CONFIG);
                }
                if (!operationEntry.getFlags().contains(OperationEntry.Flag.READ_ONLY)) {
                    effects.add(Action.ActionEffect.WRITE_RUNTIME);
                    if (!operationEntry.getFlags().contains(OperationEntry.Flag.RUNTIME_ONLY)) {
                        effects.add(Action.ActionEffect.WRITE_CONFIG);
                    }
                }
            }
            return effects;
        }
    }
}
