/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;

import java.util.EnumSet;

import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Encapsulates the handling of the common elements in the {@code operation-headers} field of a request.
 * This currently doesn't handle all known headers, but perhaps could if there is a need. Right now it is
 * focused on those used by ModelControllerImpl directly.
 *
 * @author Brian Stansberry
 */
final class OperationHeaders {

    private static final AttributeDefinition ROLLBACK = SimpleAttributeDefinitionBuilder.create(ROLLBACK_ON_RUNTIME_FAILURE, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(new ModelNode(true))
            .build();

    private static final AttributeDefinition RESTART = SimpleAttributeDefinitionBuilder.create(ALLOW_RESOURCE_SERVICE_RESTART, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(new ModelNode(false))
            .build();

    private static final AttributeDefinition BLOCKING = SimpleAttributeDefinitionBuilder.create(BLOCKING_TIMEOUT, ModelType.INT)
            .setRequired(false)
            .build();

    static OperationHeaders fromOperation(ModelNode operation) throws OperationFailedException {

        final EnumSet<OperationContextImpl.ContextFlag> contextFlags;
        Integer blockingTimeout = null;

        if (operation.hasDefined(OPERATION_HEADERS)) {
            final ModelNode headers = operation.get(OPERATION_HEADERS).clone();

            final boolean rollbackOnFailure = ROLLBACK.resolveModelAttribute(ExpressionResolver.REJECTING, headers).asBoolean();
            final boolean restartResourceServices = RESTART.resolveModelAttribute(ExpressionResolver.REJECTING, headers).asBoolean();
            contextFlags = rollbackOnFailure ? EnumSet.of(AbstractOperationContext.ContextFlag.ROLLBACK_ON_FAIL) : EnumSet.noneOf(OperationContextImpl.ContextFlag.class);
            if (restartResourceServices) {
                contextFlags.add(AbstractOperationContext.ContextFlag.ALLOW_RESOURCE_SERVICE_RESTART);
            }

            final ModelNode blockingTimeoutConfig = BLOCKING.resolveModelAttribute(ExpressionResolver.REJECTING, headers);
            if (blockingTimeoutConfig.isDefined()) {
                blockingTimeout = blockingTimeoutConfig.asInt();
                // Check the value is positive. We could use a ParameterValidator on the AttributeDefinition to do this but since
                // in the past we used this particular failure message, let's stick with it just to not change things.
                // Note that failure message compatibility has never been a requirement though, so we could change this.
                if (blockingTimeout < 1) {
                    throw ControllerLogger.MGMT_OP_LOGGER.invalidBlockingTimeout(blockingTimeout.longValue(), BLOCKING_TIMEOUT);
                }
            }

        } else {
            contextFlags = EnumSet.of(AbstractOperationContext.ContextFlag.ROLLBACK_ON_FAIL);
        }

        return new OperationHeaders(contextFlags, blockingTimeout);
    }

    static OperationHeaders forBoot(boolean rollbackOnRuntimeFailure) {
        EnumSet<OperationContextImpl.ContextFlag> contextFlags = rollbackOnRuntimeFailure
                ? EnumSet.of(AbstractOperationContext.ContextFlag.ROLLBACK_ON_FAIL)
                : EnumSet.noneOf(OperationContextImpl.ContextFlag.class);
        return new OperationHeaders(contextFlags, null);
    }

    static OperationResponse fromFailure(OperationFailedException ofe) {
        ModelNode responseNode = new ModelNode();
        responseNode.get(OUTCOME).set(FAILED);
        responseNode.get(FAILURE_DESCRIPTION).set(ofe.getFailureDescription());
        return OperationResponse.Factory.createSimple(responseNode);
    }

    private final EnumSet<AbstractOperationContext.ContextFlag> contextFlags;
    private final Integer blockingTimeout;

    private OperationHeaders(EnumSet<AbstractOperationContext.ContextFlag> contextFlags, Integer blockingTimeout) {
        this.contextFlags = contextFlags;
        this.blockingTimeout = blockingTimeout;
    }

    EnumSet<AbstractOperationContext.ContextFlag> getContextFlags() {
        return contextFlags;
    }

    Integer getBlockingTimeout() {
        return blockingTimeout;
    }
}
