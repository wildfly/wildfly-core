/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.operations.global.EnhancedSyntaxSupport.containsEnhancedSyntax;
import static org.jboss.as.controller.operations.global.EnhancedSyntaxSupport.extractAttributeName;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.NAME;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.VALUE;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} writing a single attribute. The required request parameter "name" represents the attribute name.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WriteAttributeHandler implements OperationStepHandler {

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(NAME, VALUE)
            .build();

    public static final OperationStepHandler INSTANCE = new WriteAttributeHandler();

    WriteAttributeHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        NAME.validateOperation(operation);
        final ModelNode nameModel = GlobalOperationAttributes.NAME.resolveModelAttribute(context, operation);
        final PathAddress address = context.getCurrentAddress();
        final ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        if (registry == null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noSuchResourceType(address));
        }
        final boolean useEnhancedSyntax = containsEnhancedSyntax(nameModel.asString(), registry);
        final String attributeName;
        final String attributeExpression;
        if (useEnhancedSyntax){
            attributeExpression = nameModel.asString();
            attributeName = extractAttributeName(nameModel.asString());
        }else{
            attributeName = nameModel.asString();
            attributeExpression = attributeName;
        }



        final AttributeAccess attributeAccess = registry.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
        if (attributeAccess == null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.unknownAttribute(attributeName));
        } else if (attributeAccess.getAccessType() != AttributeAccess.AccessType.READ_WRITE) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.attributeNotWritable(attributeName));
        } else {

            // Authorize
            ModelNode currentValue;
            if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                ModelNode model = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel();
                currentValue = model.has(attributeName) ? model.get(attributeName) : new ModelNode();
            } else {
                currentValue = new ModelNode();
            }
            AuthorizationResult authorizationResult = context.authorize(operation, attributeName, currentValue);
            if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
                throw ControllerLogger.ROOT_LOGGER.unauthorized(operation.require(OP).asString(), address, authorizationResult.getExplanation());
            }

            if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION
                    && !registry.isRuntimeOnly()) {
                // if the attribute is stored in the configuration, we can read its
                // old and new value from the resource's model before and after executing its write handler
                final ModelNode oldValue = currentValue.clone();
                doExecuteInternal(context, operation, attributeAccess, attributeName, currentValue, useEnhancedSyntax, attributeExpression);
                ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                ModelNode newValue = model.has(attributeName) ? model.get(attributeName) : new ModelNode();
                emitAttributeValueWrittenNotification(context, address, attributeName, oldValue, newValue);

            } else {
                assert attributeAccess.getStorageType() == AttributeAccess.Storage.RUNTIME;

                // if the attribute is a runtime attribute, its old and new values must
                // be read using the attribute's read handler and the write operation
                // must be sandwiched between the 2 calls to the read handler.
                // Each call to the read handlers will have their own results while
                // the call to the write handler will use this OSH context result.

                OperationContext.Stage currentStage = context.getCurrentStage();

                final ModelNode readAttributeOperation = Util.createOperation(READ_ATTRIBUTE_OPERATION, address);
                readAttributeOperation.get(NAME.getName()).set(attributeName);
                ReadAttributeHandler readAttributeHandler = new ReadAttributeHandler(null, null, false);

                // create 2 model nodes to store the result of the read-attribute operations
                // before and after writing the value
                final ModelNode oldValue = new ModelNode();
                final ModelNode newValue = new ModelNode();

                // We're going to add a bunch of steps, but we want them to execute right away
                // so we use the 'addFirst=true' param to addStep. That means we add them
                // in reverse order of how they will execute

                // 4th OSH is to emit the notification
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        // aggregate data from the 2 read-attribute operations
                        emitAttributeValueWrittenNotification(context, address, attributeName, oldValue.get(RESULT), newValue.get(RESULT));
                    }
                }, currentStage, true);

                // 3rd OSH is to read the new value
                context.addStep(newValue, readAttributeOperation, readAttributeHandler, currentStage, true);

                // 2nd OSH is to write the value
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        doExecuteInternal(context, operation, attributeAccess, attributeName, oldValue.get(RESULT), useEnhancedSyntax, attributeExpression);
                    }
                }, currentStage, true);

                // 1st OSH is to read the old value
                context.addStep(oldValue, readAttributeOperation, readAttributeHandler, currentStage, true);
            }
        }
    }

    private void doExecuteInternal(OperationContext context, ModelNode operation, AttributeAccess attributeAccess, String attributeName, ModelNode currentValue, boolean useEnhancedSyntax, String attributeExpression) throws OperationFailedException {
        if (useEnhancedSyntax){
            if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                operation = getEnhancedSyntaxResolvedOperation(operation, currentValue, attributeName, attributeExpression,
                        attributeAccess.getAttributeDefinition());
            } else {
                assert attributeAccess.getStorageType() == AttributeAccess.Storage.RUNTIME;

                // Resolution must be postponed to RUNTIME stage for Storage.RUNTIME attributes.

                // We have to invoke on the AttributeAccess' write handler in Stage.MODEL but we don't know yet what the value
                // we want to write. We can't know that until Stage.RUNTIME.
                // So, for Stage.MODEL we are going to pass to the AttributeAccess' write handler an operation that amounts to
                // writing the same value as exists now. The Stage.MODEL handling for a Storage.RUNTIME attribute is basically
                // meaningless, so passing in an operation whose value amounts to a no-op should be ok (although kludgy).
                // But before calling that handler we will register a Stage.RUNTIME step that will change the 'operation' object to
                // the properly resolved operation value. The step we add will execute *before* any Stage.RUNTIME step the
                // write handler adds, so the one the write handler adds will see the operation state that our step will set up.

                final ModelNode originalOperation = operation;
                final ModelNode resolvedOperation = operation.clone();
                resolvedOperation.get(ModelDescriptionConstants.NAME).set(attributeName); // replace the current expression with the base attribute name
                resolvedOperation.get(ModelDescriptionConstants.VALUE).set(currentValue); // just pass in the existing value
                operation = resolvedOperation;

                context.addStep((context1, operation1) -> {
                    ModelNode resolved = getEnhancedSyntaxResolvedOperation(originalOperation, currentValue, attributeName,
                            attributeExpression, attributeAccess.getAttributeDefinition());
                    resolvedOperation.get(ModelDescriptionConstants.NAME).set(resolved.get(ModelDescriptionConstants.NAME));
                    resolvedOperation.get(ModelDescriptionConstants.VALUE).set(resolved.get(ModelDescriptionConstants.VALUE));
                }, OperationContext.Stage.RUNTIME);
            }
        }

        OperationStepHandler writeHandler = attributeAccess.getWriteHandler();
        ClassLoader oldTccl = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(writeHandler.getClass());
        try {
            writeHandler.execute(context, operation);
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(oldTccl);
        }
    }
    private void emitAttributeValueWrittenNotification(OperationContext context, PathAddress address, String attributeName, ModelNode oldValue, ModelNode newValue) {
        // only emit a notification if the value has been successfully changed
        if (oldValue.equals(newValue)) {
            return;
        }
        ModelNode data = new ModelNode();
        data.get(NAME.getName()).set(attributeName);
        data.get(GlobalNotifications.OLD_VALUE).set(oldValue);
        data.get(GlobalNotifications.NEW_VALUE).set(newValue);
        Notification notification = new Notification(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, address, ControllerLogger.ROOT_LOGGER.attributeValueWritten(attributeName, oldValue, newValue), data);
        context.emit(notification);
    }

    private ModelNode getEnhancedSyntaxResolvedOperation(ModelNode originalOperation, ModelNode currentModel,
                                                         String attributeName, String attributeExpression,
                                                         AttributeDefinition attributeDefinition) throws OperationFailedException {
        ModelNode writeOp = originalOperation.clone();
        ModelNode diffValue =  originalOperation.get(ModelDescriptionConstants.VALUE);
        ModelNode old = new ModelNode();
        old.get(attributeName).set(currentModel);
        ModelNode fullValue = EnhancedSyntaxSupport.updateWithEnhancedSyntax(attributeExpression, old, diffValue, attributeDefinition);
        writeOp.get(ModelDescriptionConstants.NAME).set(attributeName);
        writeOp.get(ModelDescriptionConstants.VALUE).set(fullValue.get(attributeName));
        return writeOp;
    }
}
