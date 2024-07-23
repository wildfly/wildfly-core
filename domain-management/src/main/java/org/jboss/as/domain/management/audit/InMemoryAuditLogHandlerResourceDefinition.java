/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.audit.InMemoryAuditLogHandler.OPERATION_DATE;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.AS_VERSION;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.REMOTE_ADDRESS;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.USER_ID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_MEMORY_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_HISTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import java.util.List;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.audit.InMemoryAuditLogHandler;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class InMemoryAuditLogHandlerResourceDefinition extends AuditLogHandlerResourceDefinition {

    public static final SimpleAttributeDefinition MAX_OPERATION_COUNT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.MAX_HISTORY, ModelType.INT)
            .setRequired(false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(10))
            .setValidator(new IntRangeValidator(0, true, true))
            .build();
    public static final AttributeDefinition HISTORY_ELEMENT = new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.OP,
            SimpleAttributeDefinitionBuilder.create(OPERATION_DATE, ModelType.STRING, false).setStorageRuntime().build(),
            SimpleAttributeDefinitionBuilder.create(AS_VERSION, ModelType.STRING, false).setStorageRuntime().build(),
            SimpleAttributeDefinitionBuilder.create(USER_ID, ModelType.STRING, false).setStorageRuntime().build(),
            SimpleAttributeDefinitionBuilder.create(DOMAIN_UUID, ModelType.STRING, false).setStorageRuntime().build(),
            SimpleAttributeDefinitionBuilder.create(ACCESS_MECHANISM, ModelType.STRING, false).setStorageRuntime().build(),
            SimpleAttributeDefinitionBuilder.create(REMOTE_ADDRESS, ModelType.STRING, false).setStorageRuntime().build(),
            SimpleAttributeDefinitionBuilder.create(OUTCOME, ModelType.STRING, false).setStorageRuntime().build(),
            SimpleListAttributeDefinition.Builder.of(OPERATIONS,
                    SimpleAttributeDefinitionBuilder.create(OP, ModelType.STRING, false).setStorageRuntime().build())
            .build())
            .build();

    public static final String OPERATION_NAME = "show-logs";
    protected static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{MAX_OPERATION_COUNT};

    public InMemoryAuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger) {
        super(auditLogger, null, PathElement.pathElement(IN_MEMORY_HANDLER),
                DomainManagementResolver.getResolver("core.management.in-memory-handler"),
                new InMemoryAuditLogHandlerAddHandler(auditLogger), new HandlerRemoveHandler(auditLogger));
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(
                new SimpleOperationDefinitionBuilder(OPERATION_NAME, getResourceDescriptionResolver())
                .setReadOnly()
                .setRuntimeOnly()
                .setReplyType(ModelType.LIST)
                .setReplyValueType(ModelType.STRING)
                .build(), new ShowInMemoryLogsHandler(auditLogger));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, new InMemoryMaxHistoryWriteHandler(auditLogger));
        }
    }

    public static ModelNode createServerAddOperation(final PathAddress address, final ModelNode fileHandler) {
        ModelNode add = Util.createAddOperation(address);
        for (AttributeDefinition def : ATTRIBUTES) {
            if (fileHandler.get(def.getName()).isDefined()) {
                add.get(def.getName()).set(fileHandler.get(def.getName()));
            }
        }
        return add;
    }

    protected static class ShowInMemoryLogsHandler extends AbstractRuntimeOnlyHandler {
        private final ManagedAuditLogger auditLogger;

        ShowInMemoryLogsHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            final String name = Util.getNameFromAddress(operation.require(OP_ADDR));
            ModelNode result = context.getResult().setEmptyList();
            List<ModelNode> items = this.auditLogger.listLastEntries(name);
            for (int i = (items.size() - 1); i >= 0; i--) {
                ModelNode entry = items.get(i);
                ModelNode configurationChange = entry.clone();
                result.add(configurationChange);
            }
        }
    }

    protected static class InMemoryAuditLogHandlerAddHandler extends AbstractAddStepHandler {

        private final ManagedAuditLogger auditLogger;

        private InMemoryAuditLogHandlerAddHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        private InMemoryAuditLogHandler createHandler(final OperationContext context, final ModelNode model) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final int maxHistory = MAX_OPERATION_COUNT.resolveModelAttribute(context, model).asInt();
            return new InMemoryAuditLogHandler(name, maxHistory);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            InMemoryAuditLogHandler handler = createHandler(context, model);
            auditLogger.getUpdater().addHandler(handler);
            auditLogger.addFormatter(handler.getFormatter());
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            auditLogger.getUpdater().rollbackChanges();
        }
    }

    protected static class InMemoryMaxHistoryWriteHandler extends AbstractWriteAttributeHandler<Void> {
        private final ManagedAuditLogger auditLogger;

        public InMemoryMaxHistoryWriteHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
            if (MAX_HISTORY.equals(attributeName)) {
                final String name = Util.getNameFromAddress(operation.require(OP_ADDR));
                auditLogger.updateInMemoryHandlerMaxHistory(name, valueToRevert.asInt());
            }
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
            if (MAX_HISTORY.equals(attributeName)) {
               final String name = Util.getNameFromAddress(operation.require(OP_ADDR));
               auditLogger.updateInMemoryHandlerMaxHistory(name, resolvedValue.asInt());
           }
           return false;
        }
    }
}
