/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.audit;

import java.util.Arrays;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.audit.AbstractFileAuditLogHandler;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="thofman@redhat.com">Tomas Hofman</a>
 */
public abstract class AbstractFileAuditLogHandlerResourceDefinition extends AuditLogHandlerResourceDefinition {

    public static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PATH, ModelType.STRING)
        .setRequired(true)
        .setAllowExpression(true)
        .setMinSize(1)
        .build();

    public static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RELATIVE_TO, ModelType.STRING)
        .setRequired(false)
        .setMinSize(1)
        .build();

    protected static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{FORMATTER, PATH, RELATIVE_TO, MAX_FAILURE_COUNT};


    public AbstractFileAuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager, PathElement pathElement, ResourceDescriptionResolver descriptionResolver,
                                                         OperationStepHandler addHandler, OperationStepHandler removeHandler) {
        super(auditLogger, pathManager, pathElement, descriptionResolver, addHandler, removeHandler);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        HandlerWriteAttributeHandler write = getWriteAttributeHandler(auditLogger, pathManager);
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, write);
        }
    }

    protected abstract HandlerWriteAttributeHandler getWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager);

    protected abstract static class AbstractFileAuditLogHandlerAddHandler extends AbstractAddStepHandler {

        protected final ManagedAuditLogger auditLogger;
        protected final PathManagerService pathManager;

        protected AbstractFileAuditLogHandlerAddHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition[] attributes) {
            super(attributes);
            this.auditLogger = auditLogger;
            this.pathManager = pathManager;
        }

        @Override
        protected void populateModel(OperationContext context, ModelNode operation, final Resource resource)
                throws OperationFailedException {

            super.populateModel(context, operation, resource);

            // Cross-resource model validation in a separate step
            context.addStep((context1, operation1) -> {
                HandlerUtil.checkNoOtherHandlerWithTheSameName(context1);
                String formatterName = resource.getModel().get(FORMATTER.getName()).asString();
                if (!HandlerUtil.lookForFormatter(context1, context1.getCurrentAddress(), formatterName)) {
                    throw DomainManagementLogger.ROOT_LOGGER.noFormatterCalled(formatterName);
                }
            }, OperationContext.Stage.MODEL);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        protected abstract AbstractFileAuditLogHandler createHandler(final PathManagerService pathManager, final OperationContext context, final ModelNode operation) throws OperationFailedException;

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            auditLogger.getUpdater().addHandler(createHandler(pathManager, context, operation));
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource)  {
            auditLogger.getUpdater().rollbackChanges();
        }
    }

    protected abstract static class AbstractFileAuditLogHandlerWriteAttributeHandler extends AuditLogHandlerResourceDefinition.HandlerWriteAttributeHandler {

        public AbstractFileAuditLogHandlerWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager) {
            super(auditLogger, pathManager);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        protected abstract AbstractFileAuditLogHandler createHandler(final PathManagerService pathManager, final OperationContext context, final ModelNode operation) throws OperationFailedException;

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
            if (!super.handleApplyAttributeRuntime(context, operation, attributeName, resolvedValue, currentValue, handbackHolder)) {
                auditLogger.getUpdater().updateHandler(createHandler(pathManager, context, operation));
            }
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
            if (!super.handlerRevertUpdateToRuntime(context, operation, attributeName, valueToRestore, valueToRevert, handback)) {
                auditLogger.getUpdater().rollbackChanges();
            }
        }
    }

    protected static <T> T[] joinArrays(final T[] array1, final T[] array2) {
        if (array2 == null)
            return array1;
        if (array1 == null)
            return array2;
        final T[] joinedArrays = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, joinedArrays, array1.length, array2.length);
        return joinedArrays;
    }

}
