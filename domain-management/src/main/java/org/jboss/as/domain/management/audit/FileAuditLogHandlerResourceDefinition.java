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
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.audit.AbstractFileAuditLogHandler;
import org.jboss.as.controller.audit.FileAuditLogHandler;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.Arrays;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class FileAuditLogHandlerResourceDefinition extends AuditLogHandlerResourceDefinition {

    public static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PATH, ModelType.STRING)
        .setAllowNull(false)
        .setAllowExpression(true)
        .setMinSize(1)
        .build();

    public static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RELATIVE_TO, ModelType.STRING)
        .setAllowNull(true)
        .setMinSize(1)
        .build();

    protected static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{FORMATTER, PATH, RELATIVE_TO, MAX_FAILURE_COUNT};


    public FileAuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager) {
        super(auditLogger, pathManager, PathElement.pathElement(FILE_HANDLER),
                DomainManagementResolver.getDeprecatedResolver(AccessAuditResourceDefinition.DEPRECATED_MESSAGE_CATEGORY, "core.management.file-handler"),
                new FileAuditLogHandlerAddHandler(auditLogger, pathManager, ATTRIBUTES), new HandlerRemoveHandler(auditLogger));
        setDeprecated(ModelVersion.create(1, 7));
    }

    public FileAuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager, PathElement pathElement, ResourceDescriptionResolver descriptionResolver,
                OperationStepHandler addHandler, OperationStepHandler removeHandler) {
        super(auditLogger, pathManager, pathElement, descriptionResolver, addHandler, removeHandler);
    }

    public static ModelNode createServerAddOperation(final PathAddress address, final ModelNode fileHandler){
        ModelNode add = Util.createAddOperation(address);
        for (AttributeDefinition def : ATTRIBUTES) {
            if (fileHandler.get(def.getName()).isDefined()) {
                add.get(def.getName()).set(fileHandler.get(def.getName()));
            }
        }
        return add;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        HandlerWriteAttributeHandler write = getWriteAttributeHandler(auditLogger, pathManager, ATTRIBUTES);
        for (AttributeDefinition def : ATTRIBUTES){
            resourceRegistration.registerReadWriteAttribute(def, null, write);
        }
    }

    protected HandlerWriteAttributeHandler getWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition... attributeDefinitions) {
        return new HandlerWriteAttributeHandler(auditLogger, pathManager, attributeDefinitions);
    }

    private static FileAuditLogHandler createFileAuditLogHandler(final PathManagerService pathManager,
                                                                 final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final String name = Util.getNameFromAddress(operation.require(OP_ADDR));
        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        final String relativeTo = model.hasDefined(RELATIVE_TO.getName()) ? RELATIVE_TO.resolveModelAttribute(context, model).asString() : null;
        final String path = PATH.resolveModelAttribute(context, model).asString();
        final String formatterName = FORMATTER.resolveModelAttribute(context, model).asString();
        final int maxFailureCount = MAX_FAILURE_COUNT.resolveModelAttribute(context, model).asInt();
        return new FileAuditLogHandler(name, formatterName, maxFailureCount, pathManager, path, relativeTo);
    }

    protected static class FileAuditLogHandlerAddHandler extends AbstractAddStepHandler {

        protected final ManagedAuditLogger auditLogger;
        protected final PathManagerService pathManager;

        protected FileAuditLogHandlerAddHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition[] attributes) {
            super(attributes);
            this.auditLogger = auditLogger;
            this.pathManager = pathManager;
        }

        @Override
        protected void populateModel(OperationContext context, ModelNode operation, final Resource resource)
                throws OperationFailedException {

            super.populateModel(context, operation, resource);

            // Cross-resource model validation in a separate step
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    HandlerUtil.checkNoOtherHandlerWithTheSameName(context);
                    String formatterName = resource.getModel().get(FORMATTER.getName()).asString();
                    if (!HandlerUtil.lookForFormatter(context, context.getCurrentAddress(), formatterName)) {
                        throw DomainManagementLogger.ROOT_LOGGER.noFormatterCalled(formatterName);
                    }
                }
            }, OperationContext.Stage.MODEL);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        protected AbstractFileAuditLogHandler createHandler(final PathManagerService pathManager, final OperationContext context, final ModelNode operation) throws OperationFailedException {
            return createFileAuditLogHandler(pathManager, context, operation);
        }

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

    protected static class HandlerWriteAttributeHandler extends AuditLogHandlerResourceDefinition.HandlerWriteAttributeHandler {

        public HandlerWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition... attributeDefinitions) {
            super(auditLogger, pathManager, attributeDefinitions);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        protected AbstractFileAuditLogHandler createHandler(final PathManagerService pathManager, final OperationContext context, final ModelNode operation) throws OperationFailedException {
            return createFileAuditLogHandler(pathManager, context, operation);
        }

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
