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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.audit.AbstractFileAuditLogHandler;
import org.jboss.as.controller.audit.FileAuditLogHandler;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class FileAuditLogHandlerResourceDefinition extends AbstractFileAuditLogHandlerResourceDefinition {

    public static final SimpleAttributeDefinition ROTATE_AT_STARTUP = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ROTATE_AT_STARTUP, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.TRUE)
            .build();

    protected static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{ROTATE_AT_STARTUP};

    protected static final AttributeDefinition[] FULL_ATTRIBUTES = joinArrays(ATTRIBUTES, AbstractFileAuditLogHandlerResourceDefinition.ATTRIBUTES);

    public FileAuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager) {
        super(auditLogger, pathManager, PathElement.pathElement(ModelDescriptionConstants.FILE_HANDLER),
                DomainManagementResolver.getDeprecatedResolver(AccessAuditResourceDefinition.DEPRECATED_MESSAGE_CATEGORY, "core.management.file-handler"),
                new FileAuditLogHandlerAddHandler(auditLogger, pathManager, FULL_ATTRIBUTES), new HandlerRemoveHandler(auditLogger));
        setDeprecated(ModelVersion.create(1, 7));
    }

    public static ModelNode createServerAddOperation(final PathAddress address, final ModelNode fileHandler){
        ModelNode add = Util.createAddOperation(address);
        for (AttributeDefinition def : FULL_ATTRIBUTES) {
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
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, write);
        }
    }

    @Override
    protected HandlerWriteAttributeHandler getWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition... attributeDefinitions) {
        return new FileAuditLogHandlerWriteAttributeHandler(auditLogger, pathManager, attributeDefinitions);
    }

    private static FileAuditLogHandler createFileAuditLogHandler(final PathManagerService pathManager,
                                                                 final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final String name = Util.getNameFromAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        final String relativeTo = model.hasDefined(RELATIVE_TO.getName()) ? RELATIVE_TO.resolveModelAttribute(context, model).asString() : null;
        final String path = PATH.resolveModelAttribute(context, model).asString();
        final String formatterName = FORMATTER.resolveModelAttribute(context, model).asString();
        final int maxFailureCount = MAX_FAILURE_COUNT.resolveModelAttribute(context, model).asInt();
        final boolean rotateAtStartup = ROTATE_AT_STARTUP.resolveModelAttribute(context, model).asBoolean();
        return new FileAuditLogHandler(name, formatterName, maxFailureCount, pathManager, path, relativeTo, rotateAtStartup);
    }

    protected static class FileAuditLogHandlerAddHandler extends AbstractFileAuditLogHandlerAddHandler {

        protected FileAuditLogHandlerAddHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition[] attributes) {
            super(auditLogger, pathManager, attributes);
        }

        @Override
        protected AbstractFileAuditLogHandler createHandler(final PathManagerService pathManager, final OperationContext context, final ModelNode operation) throws OperationFailedException {
            return createFileAuditLogHandler(pathManager, context, operation);
        }
    }

    protected static class FileAuditLogHandlerWriteAttributeHandler extends AbstractFileAuditLogHandlerWriteAttributeHandler {

        public FileAuditLogHandlerWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition... attributeDefinitions) {
            super(auditLogger, pathManager, attributeDefinitions);
        }

        protected AbstractFileAuditLogHandler createHandler(final PathManagerService pathManager, final OperationContext context, final ModelNode operation) throws OperationFailedException {
            return createFileAuditLogHandler(pathManager, context, operation);
        }
    }

}
