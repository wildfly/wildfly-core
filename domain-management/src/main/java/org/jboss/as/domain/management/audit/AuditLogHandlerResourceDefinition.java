/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_MEMORY_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SIZE_ROTATING_FILE_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSLOG_HANDLER;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class AuditLogHandlerResourceDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition FORMATTER = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.FORMATTER, ModelType.STRING)
        .setRequired(true)
        .setMinSize(1)
        .build();

    public static final SimpleAttributeDefinition MAX_FAILURE_COUNT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MAX_FAILURE_COUNT, ModelType.INT)
        .setRequired(false)
        .setAllowExpression(true)
        .setDefaultValue(new ModelNode(10))
        .setValidator(new IntRangeValidator(0, true, true))
        .build();

    public static final SimpleAttributeDefinition FAILURE_COUNT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.FAILURE_COUNT, ModelType.INT)
        .setRequired(true)
        .setStorageRuntime()
        .setRuntimeServiceNotRequired()
        .build();

    public static final SimpleAttributeDefinition DISABLED_DUE_TO_FAILURE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.DISABLED_DUE_TO_FAILURE, ModelType.BOOLEAN)
        .setRequired(true)
        .setStorageRuntime()
        .setRuntimeServiceNotRequired()
        .build();


    private static final AttributeDefinition[] RUNTIME_ATTRIBUTES = new AttributeDefinition[] {FAILURE_COUNT, DISABLED_DUE_TO_FAILURE};

    static final String[] HANDLER_TYPES = new String[] {FILE_HANDLER, SYSLOG_HANDLER, PERIODIC_ROTATING_FILE_HANDLER, SIZE_ROTATING_FILE_HANDLER, IN_MEMORY_HANDLER};

    protected final ManagedAuditLogger auditLogger;
    protected final PathManagerService pathManager;


    AuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager, PathElement pathElement, ResourceDescriptionResolver descriptionResolver,
            OperationStepHandler addHandler, OperationStepHandler removeHandler) {
        super(pathElement, descriptionResolver, addHandler, removeHandler);
        this.auditLogger = auditLogger;
        this.pathManager = pathManager;
    }

    AuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager, Parameters parameters) {
        super(parameters);
        this.auditLogger = auditLogger;
        this.pathManager = pathManager;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : RUNTIME_ATTRIBUTES) {
            resourceRegistration.registerReadOnlyAttribute(def, new HandlerRuntimeAttributeHandler(auditLogger));
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(
                new SimpleOperationDefinitionBuilder(
                        ModelDescriptionConstants.RECYCLE, getResourceDescriptionResolver())
                            .setRuntimeOnly().
                            build(),
                new HandlerRecycleHandler(auditLogger));
    }

    private static class HandlerRuntimeAttributeHandler extends AbstractRuntimeOnlyHandler {
        private final ManagedAuditLogger auditLogger;

        HandlerRuntimeAttributeHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            String attr = operation.require(ModelDescriptionConstants.NAME).asString();
            String handlerName = Util.getNameFromAddress(operation.require(OP_ADDR));
            if (attr.equals(FAILURE_COUNT.getName())) {
                context.getResult().set(auditLogger.getHandlerFailureCount(handlerName));
            } else if (attr.equals(DISABLED_DUE_TO_FAILURE.getName())) {
                context.getResult().set(auditLogger.getHandlerDisabledDueToFailure(handlerName));
            }
        }
    }

    private static class HandlerRecycleHandler extends AbstractRuntimeOnlyHandler {
        private final ManagedAuditLogger auditLogger;

        HandlerRecycleHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            auditLogger.recycleHandler(Util.getNameFromAddress(operation.require(OP_ADDR)));
        }
    }

    static class HandlerRemoveHandler extends AbstractRemoveStepHandler {
        private final ManagedAuditLogger auditLogger;

        HandlerRemoveHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            auditLogger.getUpdater().removeHandler(Util.getNameFromAddress(operation.require(OP_ADDR)));
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            auditLogger.getUpdater().rollbackChanges();
        }
    }

    abstract static class HandlerWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {
        final ManagedAuditLogger auditLogger;
        final PathManagerService pathManager;

        HandlerWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition... attributeDefinitions) {
            super(attributeDefinitions);
            this.auditLogger = auditLogger;
            this.pathManager = pathManager;
        }

        @Override
        protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
                ModelNode oldValue, Resource model) throws OperationFailedException {
            if (attributeName.equals(FORMATTER.getName())) {
                String formatterName = newValue.asString();
                if (!HandlerUtil.lookForFormatter(context, PathAddress.pathAddress(operation.require(OP_ADDR)), formatterName)) {
                    throw DomainManagementLogger.ROOT_LOGGER.noFormatterCalled(formatterName);
                }
            }
        }

        boolean handleApplyAttributeRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
            if (attributeName.equals(FORMATTER.getName())) {
                auditLogger.updateHandlerFormatter(Util.getNameFromAddress(operation.require(OP_ADDR)), resolvedValue.asString());
                return true;
            } else if (attributeName.equals(MAX_FAILURE_COUNT.getName())) {
                auditLogger.updateHandlerMaxFailureCount(Util.getNameFromAddress(operation.require(OP_ADDR)), resolvedValue.asInt());
                return true;
            }
            return false;
        }


        boolean handlerRevertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
            if (attributeName.equals(FORMATTER.getName())) {
                auditLogger.updateHandlerFormatter(Util.getNameFromAddress(operation.require(OP_ADDR)), valueToRestore.asString());
                return true;
            } else if (attributeName.equals(MAX_FAILURE_COUNT.getName())) {
                auditLogger.updateHandlerMaxFailureCount(Util.getNameFromAddress(operation.require(OP_ADDR)), valueToRestore.asInt());
                return true;
            }
            return false;
        }

    }

}
