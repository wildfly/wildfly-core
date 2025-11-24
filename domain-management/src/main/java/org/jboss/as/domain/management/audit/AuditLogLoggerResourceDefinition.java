/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.audit;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the management audit logging resource.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class AuditLogLoggerResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(ModelDescriptionConstants.LOGGER, ModelDescriptionConstants.AUDIT_LOG);

    public static final PathElement HOST_SERVER_PATH_ELEMENT = PathElement.pathElement(ModelDescriptionConstants.SERVER_LOGGER, ModelDescriptionConstants.AUDIT_LOG);

    public static final SimpleAttributeDefinition LOG_BOOT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.LOG_BOOT, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.TRUE).build();


    public static final SimpleAttributeDefinition LOG_READ_ONLY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.LOG_READ_ONLY, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE).build();

    public static final SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ENABLED, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE).build();

    static final List<SimpleAttributeDefinition> ATTRIBUTE_DEFINITIONS = Arrays.asList(LOG_BOOT, LOG_READ_ONLY, ENABLED);

    private final ManagedAuditLogger auditLogger;

    private AuditLogLoggerResourceDefinition(final PathElement pathElement, final ManagedAuditLogger auditLogger) {
        super(pathElement,
                DomainManagementResolver.getResolver( "core.management.audit-log"),
                new AuditLogLoggerAddHandler(auditLogger), new AuditLogLoggerRemoveHandler(auditLogger));
        this.auditLogger = auditLogger;
    }

    static AuditLogLoggerResourceDefinition createDefinition(ManagedAuditLogger auditLogger){
        return new AuditLogLoggerResourceDefinition(PATH_ELEMENT, auditLogger);
    }

    static AuditLogLoggerResourceDefinition createHostServerDefinition(){
        return new AuditLogLoggerResourceDefinition(HOST_SERVER_PATH_ELEMENT, null);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        //This one only takes effect at boot
        resourceRegistration.registerReadWriteAttribute(LOG_BOOT, null, ModelOnlyWriteAttributeHandler.INSTANCE);

        resourceRegistration.registerReadWriteAttribute(LOG_READ_ONLY, null, new AuditLogReadOnlyWriteAttributeHandler(auditLogger));
        resourceRegistration.registerReadWriteAttribute(ENABLED, null, new AuditLogEnabledWriteAttributeHandler(auditLogger));
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new AuditLogHandlerReferenceResourceDefinition(auditLogger));
    }


    public static void createServerAddOperations(List<ModelNode> addOps, PathAddress loggerAddress, ModelNode logger) {
        addOps.add(createLoggerAddOperation(loggerAddress, logger));

        final String handler = AuditLogHandlerReferenceResourceDefinition.PATH_ELEMENT.getKey();
        if (logger.hasDefined(handler)){
            for (Property prop : logger.get(handler).asPropertyList()) {
                addOps.add(Util.createAddOperation(loggerAddress.append(PathElement.pathElement(handler, prop.getName()))));
            }
        }
    }

    public static ModelNode createLoggerAddOperation(PathAddress loggerAddress, ModelNode logger){
        ModelNode addOp = Util.createAddOperation(loggerAddress);
        for (AttributeDefinition def : ATTRIBUTE_DEFINITIONS){
            addOp.get(def.getName()).set(logger.get(def.getName()));
        }
        return addOp;
    }

    private static class AuditLogLoggerAddHandler implements OperationStepHandler {

        private final ManagedAuditLogger auditLoggerProvider;

        AuditLogLoggerAddHandler(ManagedAuditLogger auditLoggerProvider) {
            this.auditLoggerProvider = auditLoggerProvider;
        }

        /** {@inheritDoc */
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
            final ModelNode model = resource.getModel();
            for (AttributeDefinition attr : AuditLogLoggerResourceDefinition.ATTRIBUTE_DEFINITIONS) {
                attr.validateAndSet(operation, model);
            }

            if (auditLoggerProvider != null) {
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                        final boolean wasReadOnly = auditLoggerProvider.isLogReadOnly();

                        auditLoggerProvider.setLogBoot(AuditLogLoggerResourceDefinition.LOG_BOOT.resolveModelAttribute(context, model).asBoolean());
                        auditLoggerProvider.setLogReadOnly(AuditLogLoggerResourceDefinition.LOG_READ_ONLY.resolveModelAttribute(context, model).asBoolean());
                        boolean enabled = AuditLogLoggerResourceDefinition.ENABLED.resolveModelAttribute(context, model).asBoolean();
                        final AuditLogger.Status status = enabled ? AuditLogger.Status.LOGGING : AuditLogger.Status.DISABLED;
                        context.completeStep((OperationContext.ResultAction resultAction, OperationContext context1, ModelNode operation1) -> {
                            if(resultAction == OperationContext.ResultAction.KEEP) {
                                auditLoggerProvider.setLoggerStatus(status);
                            } else {
                                auditLoggerProvider.setLogReadOnly(wasReadOnly);
                            }
                        });
                    }
                }, OperationContext.Stage.RUNTIME);
            }
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    private static class AuditLogLoggerRemoveHandler extends AbstractRemoveStepHandler {

        private final ManagedAuditLogger auditLogger;

        AuditLogLoggerRemoveHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {

            // This is a hack. We want the Stage.MODEL behavior from the superclass but the
            // way it deals with Stage.RUNTIME via performRuntime and recoverServices doesn't
            // work for us. So we hack requiresRuntime to do what we want and then return false
            // to turn off the superclass work
            if (auditLogger != null) {

                context.addStep(new OperationStepHandler() {
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                        final boolean wasReadOnly = auditLogger.isLogReadOnly();
                        final AuditLogger.Status oldStatus = auditLogger.getLoggerStatus();

                        auditLogger.setLoggerStatus(AuditLogger.Status.DISABLE_NEXT);

                        context.completeStep(new OperationContext.RollbackHandler() {
                            @Override
                            public void handleRollback(OperationContext context, ModelNode operation) {
                                auditLogger.setLogReadOnly(wasReadOnly);
                                auditLogger.setLoggerStatus(oldStatus);
                            }
                        });
                    }
                }, OperationContext.Stage.RUNTIME);
            }

            return false;
        }
    }

    private static class AuditLogEnabledWriteAttributeHandler extends AbstractWriteAttributeHandler<ManagedAuditLogger.Status> {

        private final ManagedAuditLogger auditLogger;

        AuditLogEnabledWriteAttributeHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return auditLogger != null;
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                               ModelNode resolvedValue, ModelNode currentValue,
                                               HandbackHolder<ManagedAuditLogger.Status> handbackHolder) throws OperationFailedException {
            handbackHolder.setHandback(auditLogger.getLoggerStatus());
            boolean enabled = resolvedValue.asBoolean();
            ManagedAuditLogger.Status status = enabled ? AuditLogger.Status.LOGGING : AuditLogger.Status.DISABLE_NEXT;
            auditLogger.setLoggerStatus(status);
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                             ModelNode valueToRestore, ModelNode valueToRevert, ManagedAuditLogger.Status handback) throws OperationFailedException {
            auditLogger.setLoggerStatus(handback);
        }
    }

    private static class AuditLogReadOnlyWriteAttributeHandler extends AbstractWriteAttributeHandler<Boolean> {

        private final ManagedAuditLogger auditLogger;

        public AuditLogReadOnlyWriteAttributeHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return auditLogger != null;
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                               ModelNode resolvedValue, ModelNode currentValue,
                                               HandbackHolder<Boolean> handbackHolder) throws OperationFailedException {
            handbackHolder.setHandback(auditLogger.isLogReadOnly());
            auditLogger.setLogReadOnly(resolvedValue.asBoolean());
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Boolean handback) throws OperationFailedException {
            auditLogger.setLogReadOnly(handback);
        }
    }

}
