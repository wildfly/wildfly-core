/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
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
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * This has subtle differences from AuditLoggerResourceDefinition in domain-management so it is not a duplicate
 *
 * {@link org.jboss.as.controller.ResourceDefinition} for the management audit logging resource.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class JmxAuditLoggerResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(CommonAttributes.CONFIGURATION, ModelDescriptionConstants.AUDIT_LOG);

    public static final SimpleAttributeDefinition LOG_BOOT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.LOG_BOOT, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE).build();

    public static final SimpleAttributeDefinition LOG_READ_ONLY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.LOG_READ_ONLY, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE).build();

    public static final SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ENABLED, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE).build();

    static final List<SimpleAttributeDefinition> AUDIT_LOG_ATTRIBUTE_DEFINITIONS = Arrays.asList(LOG_BOOT, LOG_READ_ONLY, ENABLED);


    private final ManagedAuditLogger auditLogger;

    public JmxAuditLoggerResourceDefinition(final ManagedAuditLogger auditLogger) {
        super(PATH_ELEMENT, JMXExtension.getResourceDescriptionResolver("audit-log"),
                new AuditLoggerAddHandler(auditLogger), new AuditLoggerRemoveHandler(auditLogger));
        this.auditLogger = auditLogger;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AuditLogWriteAttributeHandler wah = new AuditLogWriteAttributeHandler(auditLogger, LOG_BOOT, LOG_READ_ONLY, ENABLED);
        resourceRegistration.registerReadWriteAttribute(LOG_BOOT, null, wah);
        resourceRegistration.registerReadWriteAttribute(LOG_READ_ONLY, null, wah);
        resourceRegistration.registerReadWriteAttribute(ENABLED, null, wah);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new JmxAuditLogHandlerReferenceResourceDefinition(auditLogger));
    }




    private static class AuditLoggerAddHandler implements OperationStepHandler {

        private final ManagedAuditLogger auditLoggerProvider;

        AuditLoggerAddHandler(ManagedAuditLogger auditLoggerProvider) {
            this.auditLoggerProvider = auditLoggerProvider;
        }

        /** {@inheritDoc */
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
            final ModelNode model = resource.getModel();
            for (AttributeDefinition attr : JmxAuditLoggerResourceDefinition.AUDIT_LOG_ATTRIBUTE_DEFINITIONS) {
                attr.validateAndSet(operation, model);
            }


            if (!PROFILE.equals(context.getCurrentAddress().getElement(0).getKey())) {
                context.addStep(new OperationStepHandler() {
                    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                        final boolean wasReadOnly = auditLoggerProvider.isLogReadOnly();
                        final boolean wasLogBoot = auditLoggerProvider.isLogBoot();
                        final AuditLogger.Status oldStatus = auditLoggerProvider.getLoggerStatus();

                        auditLoggerProvider.setLogReadOnly(JmxAuditLoggerResourceDefinition.LOG_READ_ONLY.resolveModelAttribute(context, model).asBoolean());
                        auditLoggerProvider.setLogBoot(JmxAuditLoggerResourceDefinition.LOG_BOOT.resolveModelAttribute(context, model).asBoolean());
                        boolean enabled = JmxAuditLoggerResourceDefinition.ENABLED.resolveModelAttribute(context, model).asBoolean();
                        auditLoggerProvider.setLoggerStatus(enabled ? AuditLogger.Status.LOGGING : AuditLogger.Status.DISABLED);

                        context.completeStep(new OperationContext.RollbackHandler() {
                            @Override
                            public void handleRollback(OperationContext context, ModelNode operation) {
                                auditLoggerProvider.setLogReadOnly(wasReadOnly);
                                auditLoggerProvider.setLoggerStatus(oldStatus);
                                auditLoggerProvider.setLogBoot(wasLogBoot);
                            }
                        });
                    }
                }, OperationContext.Stage.RUNTIME);
            }

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }


    private static class AuditLoggerRemoveHandler implements OperationStepHandler {

        private final ManagedAuditLogger auditLogger;

        AuditLoggerRemoveHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            context.removeResource(PathAddress.EMPTY_ADDRESS);

            if (!PROFILE.equals(context.getCurrentAddress().getElement(0).getKey())) {
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

            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    private static class AuditLogWriteAttributeHandler extends AbstractWriteAttributeHandler<Object>{

        private final ManagedAuditLogger auditLogger;

        AuditLogWriteAttributeHandler(ManagedAuditLogger auditLogger, AttributeDefinition...attributeDefinitions) {
            super(attributeDefinitions);
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                ModelNode resolvedValue, ModelNode currentValue,
                org.jboss.as.controller.AbstractWriteAttributeHandler.HandbackHolder<Object> handbackHolder)
                throws OperationFailedException {
            if (attributeName.equals(LOG_BOOT.getName())) {
                handbackHolder.setHandback(auditLogger.isLogBoot());
                auditLogger.setLogBoot(resolvedValue.asBoolean());

            } else if (attributeName.equals(ENABLED.getName())) {
                handbackHolder.setHandback(auditLogger.getLoggerStatus());
                boolean enabled = resolvedValue.asBoolean();
                ManagedAuditLogger.Status status = enabled ? AuditLogger.Status.LOGGING : AuditLogger.Status.DISABLED;
                auditLogger.setLoggerStatus(status);

            } else if (attributeName.equals(LOG_READ_ONLY.getName())){
                handbackHolder.setHandback(auditLogger.isLogReadOnly());
                auditLogger.setLogReadOnly(resolvedValue.asBoolean());
            }
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
            if (attributeName.equals(LOG_BOOT.getName())) {
                auditLogger.setLogBoot((Boolean)handback);
            } else if (attributeName.equals(ENABLED.getName())) {
                auditLogger.setLoggerStatus((ManagedAuditLogger.Status)handback);

            } else if (attributeName.equals(LOG_READ_ONLY.getName())){
                auditLogger.setLogReadOnly((Boolean)handback);
            }
        }
    }
}
