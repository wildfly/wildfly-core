/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.audit;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.audit.AbstractFileAuditLogHandler;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.audit.PeriodicRotatingFileAuditLogHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.audit.validators.SuffixValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER;

/**
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public class PeriodicRotatingFileAuditLogHandlerResourceDefinition extends AbstractFileAuditLogHandlerResourceDefinition {

    public static final SimpleAttributeDefinition SUFFIX = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SUFFIX, ModelType.STRING)
        .setRequired(true)
        .setAllowExpression(true)
        .setValidator(new SuffixValidator())
        .setMinSize(1)
        .build();

    protected static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{SUFFIX};

    protected static final AttributeDefinition[] FULL_ATTRIBUTES = joinArrays(ATTRIBUTES, AbstractFileAuditLogHandlerResourceDefinition.ATTRIBUTES);

    public PeriodicRotatingFileAuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager) {
        super(auditLogger, pathManager, PathElement.pathElement(PERIODIC_ROTATING_FILE_HANDLER), DomainManagementResolver.getResolver("core.management.periodic-rotating-file-handler"),
                new PeriodicRotatingFileAuditLogHandlerAddHandler(auditLogger, pathManager, FULL_ATTRIBUTES), new HandlerRemoveHandler(auditLogger));
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
        HandlerWriteAttributeHandler write = getWriteAttributeHandler(auditLogger, pathManager);
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, write);
        }
    }

    @Override
    protected HandlerWriteAttributeHandler getWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager) {
        return new PeriodicRotatingHandlerWriteAttributeHandler(auditLogger, pathManager);
    }

    private static PeriodicRotatingFileAuditLogHandler createPeriodicRotatingFileAuditLogHandler(final PathManagerService pathManager,
                                                                                                 final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final String name = Util.getNameFromAddress(operation.require(OP_ADDR));
        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        final String relativeTo = model.hasDefined(RELATIVE_TO.getName()) ? RELATIVE_TO.resolveModelAttribute(context, model).asString() : null;
        final String path = PATH.resolveModelAttribute(context, model).asString();
        final String formatterName = FORMATTER.resolveModelAttribute(context, model).asString();
        final int maxFailureCount = MAX_FAILURE_COUNT.resolveModelAttribute(context, model).asInt();
        final String suffix = SUFFIX.resolveModelAttribute(context, model).asString();
        return new PeriodicRotatingFileAuditLogHandler(name, formatterName, maxFailureCount, pathManager, path, relativeTo, suffix, /*default timeZone*/null);
    }

    protected static class PeriodicRotatingFileAuditLogHandlerAddHandler extends AbstractFileAuditLogHandlerAddHandler {

        protected PeriodicRotatingFileAuditLogHandlerAddHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition[] attributes) {
            super(auditLogger, pathManager, attributes);
        }

        @Override
        protected AbstractFileAuditLogHandler createHandler(final PathManagerService pathManager, final OperationContext context, final ModelNode operation) throws OperationFailedException {
            return createPeriodicRotatingFileAuditLogHandler(pathManager, context, operation);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            auditLogger.getUpdater().addHandler(createHandler(pathManager, context, operation));
        }

    }

    protected static class PeriodicRotatingHandlerWriteAttributeHandler extends AbstractFileAuditLogHandlerWriteAttributeHandler {

        public PeriodicRotatingHandlerWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager) {
            super(auditLogger, pathManager);
        }

        @Override
        protected AbstractFileAuditLogHandler createHandler(final PathManagerService pathManager, final OperationContext context, final ModelNode operation) throws OperationFailedException {
            return createPeriodicRotatingFileAuditLogHandler(pathManager, context, operation);
        }
    }
}
