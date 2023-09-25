/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.core.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONFIGURATION_CHANGES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ConfigurationChangesCollector;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
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
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Resource to list all configuration changes.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ConfigurationChangeResourceDefinition extends PersistentResourceDefinition {

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
    public static final ConfigurationChangeResourceDefinition INSTANCE = new ConfigurationChangeResourceDefinition();

    private ConfigurationChangeResourceDefinition() {
        super(new SimpleResourceDefinition.Parameters(PATH, CoreManagementExtension.getResourceDescriptionResolver(CONFIGURATION_CHANGES))
                .setCapabilities(CONFIGURATION_CHANGES_CAPABILITY)
                .setAddHandler(new ConfigurationChangeResourceAddHandler())
                .setRemoveHandler(new ConfigurationChangeResourceRemoveHandler()));
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(ConfigurationChangesHandler.DEFINITION, ConfigurationChangesHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(MAX_HISTORY, null, new MaxHistoryWriteHandler(ConfigurationChangesCollector.INSTANCE));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptyList();
    }


    private static class ConfigurationChangeResourceAddHandler extends AbstractAddStepHandler {
        private ConfigurationChangeResourceAddHandler() {
            super(MAX_HISTORY);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            super.performRuntime(context, operation, resource);
            context.getServiceTarget().addService(CONFIGURATION_CHANGES_CAPABILITY.getCapabilityServiceName()).install();
            ModelNode maxHistory = MAX_HISTORY.resolveModelAttribute(context, operation);
            ConfigurationChangesCollector.INSTANCE.setMaxHistory(maxHistory.asInt());
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return context.isDefaultRequiresRuntime();
        }
    }

    private static class ConfigurationChangeResourceRemoveHandler extends AbstractRemoveStepHandler {

        private ConfigurationChangeResourceRemoveHandler() {
            super();
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return context.isDefaultRequiresRuntime();
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
            ConfigurationChangesCollector.INSTANCE.deactivate();
            context.removeService(CONFIGURATION_CHANGES_CAPABILITY.getCapabilityServiceName());
        }
    }

    private static class MaxHistoryWriteHandler extends AbstractWriteAttributeHandler<Integer> {

        private final ConfigurationChangesCollector collector;

        private MaxHistoryWriteHandler(ConfigurationChangesCollector collector) {
            super(MAX_HISTORY);
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

    private static class ConfigurationChangesHandler extends AbstractRuntimeOnlyHandler {

        private static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME,
                CoreManagementExtension.getResourceDescriptionResolver(CONFIGURATION_CHANGES))
                .setReplyType(ModelType.STRING)
                .setRuntimeOnly()
                .build();
        private static final ConfigurationChangesHandler INSTANCE = new ConfigurationChangesHandler(ConfigurationChangesCollector.INSTANCE);
        private static final Set<Action.ActionEffect> ADDRESS_EFFECT = EnumSet.of(Action.ActionEffect.ADDRESS);
        private static final Set<Action.ActionEffect> READ_EFFECT = EnumSet.of(Action.ActionEffect.READ_CONFIG, Action.ActionEffect.READ_RUNTIME);

        private final ConfigurationChangesCollector collector;

        private ConfigurationChangesHandler(ConfigurationChangesCollector collector) {
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
