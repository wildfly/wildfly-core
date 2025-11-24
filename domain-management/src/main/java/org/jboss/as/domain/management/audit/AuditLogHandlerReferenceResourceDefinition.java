/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class AuditLogHandlerReferenceResourceDefinition extends SimpleResourceDefinition {

    static final PathElement PATH_ELEMENT = PathElement.pathElement(HANDLER);

    public AuditLogHandlerReferenceResourceDefinition(ManagedAuditLogger auditLogger) {
        super(PATH_ELEMENT,
                DomainManagementResolver.getResolver( "core.management.audit-log.handler-reference"),
                new AuditLogHandlerReferenceAddHandler(auditLogger), new AuditLogHandlerReferenceRemoveHandler(auditLogger));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
    }

    private static class AuditLogHandlerReferenceAddHandler extends AbstractAddStepHandler {
        private final ManagedAuditLogger auditLogger;

        AuditLogHandlerReferenceAddHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
            // TODO use capability based reference validation
            final PathAddress addr = PathAddress.pathAddress(operation.require(OP_ADDR));
            String name = addr.getLastElement().getValue();
            if (!HandlerUtil.lookForHandler(context, addr, name)) {
                throw DomainManagementLogger.ROOT_LOGGER.noHandlerCalled(name);
            }
            resource.getModel().setEmptyObject();
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return auditLogger != null;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            auditLogger.getUpdater().addHandlerReference(PathAddress.pathAddress(operation.require(OP_ADDR)));
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            auditLogger.getUpdater().rollbackChanges();
        }

    }

    private static class AuditLogHandlerReferenceRemoveHandler extends AbstractRemoveStepHandler {
        private final ManagedAuditLogger auditLogger;

        AuditLogHandlerReferenceRemoveHandler(ManagedAuditLogger auditLogger){
            this.auditLogger = auditLogger;

        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return auditLogger != null;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            auditLogger.getUpdater().removeHandlerReference(PathAddress.pathAddress(operation.require(OP_ADDR)));
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            auditLogger.getUpdater().rollbackChanges();
        }
    }
}
