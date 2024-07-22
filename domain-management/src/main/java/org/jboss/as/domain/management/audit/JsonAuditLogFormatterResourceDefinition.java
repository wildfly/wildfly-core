/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FORMATTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JSON_FORMATTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.domain.management.audit.AuditLogHandlerResourceDefinition.HANDLER_TYPES;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.audit.JsonAuditLogItemFormatter;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class JsonAuditLogFormatterResourceDefinition extends SimpleResourceDefinition {

    private final ManagedAuditLogger auditLogger;

    public static final SimpleAttributeDefinition INCLUDE_DATE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INCLUDE_DATE, ModelType.BOOLEAN)
        .setRequired(false)
        .setDefaultValue(ModelNode.TRUE)
        .setAllowExpression(true)
        .build();

    public static final SimpleAttributeDefinition DATE_FORMAT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.DATE_FORMAT, ModelType.STRING)
        .setRequired(false)
        .setDefaultValue(new ModelNode("yyyy-MM-dd HH:mm:ss"))
        .setAllowExpression(true)
        .setMinSize(1)
        .build();

    public static final SimpleAttributeDefinition DATE_SEPARATOR = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.DATE_SEPARATOR, ModelType.STRING)
        .setRequired(false)
        .setDefaultValue(new ModelNode(" - "))
        .setAllowExpression(true)
        .setMinSize(1)
        .build();

    public static final SimpleAttributeDefinition COMPACT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.COMPACT, ModelType.BOOLEAN)
        .setRequired(false)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(true)
        .build();

    public static final SimpleAttributeDefinition ESCAPE_NEW_LINE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ESCAPE_NEW_LINE, ModelType.BOOLEAN)
        .setRequired(false)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(true)
        .build();

    public static final SimpleAttributeDefinition ESCAPE_CONTROL_CHARACTERS = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ESCAPE_CONTROL_CHARACTERS, ModelType.BOOLEAN)
        .setRequired(false)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(true)
        .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{INCLUDE_DATE, DATE_FORMAT, DATE_SEPARATOR, COMPACT, ESCAPE_NEW_LINE, ESCAPE_CONTROL_CHARACTERS};

    public JsonAuditLogFormatterResourceDefinition(ManagedAuditLogger auditLogger) {
        super(PathElement.pathElement(JSON_FORMATTER),
                DomainManagementResolver.getDeprecatedResolver(AccessAuditResourceDefinition.DEPRECATED_MESSAGE_CATEGORY, "core.management.json-formatter"),
                new JsonAuditLogFormatterAddHandler(auditLogger), new JsonAuditLogFormatterRemoveHandler(auditLogger));
        this.auditLogger = auditLogger;
        setDeprecated(ModelVersion.create(1, 7));
    }

    public static ModelNode createServerAddOperation(final PathAddress address, final ModelNode formatter){
        ModelNode add = Util.createAddOperation(address);
        for (AttributeDefinition def : ATTRIBUTES) {
            if (formatter.hasDefined(def.getName())) {
                add.get(def.getName()).set(formatter.get(def.getName()));
            }
        }
        return add;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        HandlerWriteAttributeHandler write = new HandlerWriteAttributeHandler(auditLogger, ATTRIBUTES);
        for (AttributeDefinition def : ATTRIBUTES){
            resourceRegistration.registerReadWriteAttribute(def, null, write);
        }
    }

    private static JsonAuditLogItemFormatter createFormatter(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        return new JsonAuditLogItemFormatter(
                Util.getNameFromAddress(operation.require(OP_ADDR)),
                INCLUDE_DATE.resolveModelAttribute(context, model).asBoolean(),
                DATE_SEPARATOR.resolveModelAttribute(context, model).asString(),
                DATE_FORMAT.resolveModelAttribute(context, model).asString(),
                COMPACT.resolveModelAttribute(context, model).asBoolean(),
                ESCAPE_NEW_LINE.resolveModelAttribute(context, model).asBoolean(),
                ESCAPE_CONTROL_CHARACTERS.resolveModelAttribute(context, model).asBoolean());
    }

    private static class JsonAuditLogFormatterAddHandler extends AbstractAddStepHandler {

        private final ManagedAuditLogger auditLogger;

        private JsonAuditLogFormatterAddHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected void populateModel(OperationContext context, ModelNode operation, Resource resource)
                throws OperationFailedException {
            //TODO once we support more types of formatters, check that there are no other ones with the same name
            super.populateModel(context, operation, resource);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            JsonAuditLogItemFormatter formatter = createFormatter(context, operation, model);
            auditLogger.addFormatter(formatter);
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource)  {
            auditLogger.removeFormatter(Util.getNameFromAddress(operation.require(OP_ADDR)));
        }
    }

    private static class JsonAuditLogFormatterRemoveHandler extends AbstractRemoveStepHandler {
        private final ManagedAuditLogger auditLogger;

        private JsonAuditLogFormatterRemoveHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected void performRemove(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            final String name = Util.getNameFromAddress(operation.require(OP_ADDR));
            final Resource auditLog = context.readResourceFromRoot(PathAddress.pathAddress(CoreManagementResourceDefinition.PATH_ELEMENT, AccessAuditResourceDefinition.PATH_ELEMENT));
            checkFormatterNotReferenced(name, auditLog, HANDLER_TYPES);
            super.performRemove(context, operation, model);
        }

        private void checkFormatterNotReferenced(String name, Resource auditLog, String...handlerTypes) throws OperationFailedException {
            for (String handlerType : handlerTypes) {
                for (ResourceEntry entry : auditLog.getChildren(handlerType)) {
                    ModelNode auditLogModel = entry.getModel();
                    if (auditLogModel.get(FORMATTER).asString().equals(name)) {
                        throw DomainManagementLogger.ROOT_LOGGER.cannotRemoveReferencedFormatter(entry.getPathElement());
                    }
                }
            }
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            auditLogger.removeFormatter(Util.getNameFromAddress(operation.require(OP_ADDR)));
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            JsonAuditLogItemFormatter formatter = createFormatter(context, operation, model);
            auditLogger.addFormatter(formatter);
        }



    }

    private static class HandlerWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {
        private final ManagedAuditLogger auditLogger;

        public HandlerWriteAttributeHandler(ManagedAuditLogger auditLogger, AttributeDefinition... attributeDefinitions) {
            super(attributeDefinitions);
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
            updateFormatter(operation, attributeName, resolvedValue);
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
            updateFormatter(operation, attributeName, valueToRestore);
        }

        private void updateFormatter(ModelNode operation, String attributeName, ModelNode value) {
            JsonAuditLogItemFormatter formatter = auditLogger.getJsonFormatter(Util.getNameFromAddress(operation.require(OP_ADDR)));
            if (attributeName.equals(INCLUDE_DATE.getName())){
                formatter.setIncludeDate(value.asBoolean());
            } else if (attributeName.equals(DATE_FORMAT.getName())){
                formatter.setDateFormat(value.asString());
            } else if (attributeName.equals(DATE_SEPARATOR.getName())){
                formatter.setDateSeparator(value.asString());
            } else if (attributeName.equals(COMPACT.getName())){
                formatter.setCompactJson(value.asBoolean());
            } else if (attributeName.equals(ESCAPE_NEW_LINE.getName())) {
                formatter.setEscapeNewLine(value.asBoolean());
            } else if (attributeName.equals(ESCAPE_CONTROL_CHARACTERS.getName())) {
                formatter.setEscapeControlCharacters(value.asBoolean());
            }
        }
    }
}
