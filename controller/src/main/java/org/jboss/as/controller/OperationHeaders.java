/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WARNING_LEVEL;

import java.util.EnumSet;

import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.core.security.AccessMechanism;
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
            .setDefaultValue(ModelNode.TRUE)
            .build();

    private static final AttributeDefinition RESTART = SimpleAttributeDefinitionBuilder.create(ALLOW_RESOURCE_SERVICE_RESTART, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    private static final AttributeDefinition BLOCKING = SimpleAttributeDefinitionBuilder.create(BLOCKING_TIMEOUT, ModelType.INT)
            .setRequired(false)
            .build();

    private static final OperationHeaders INTERNAL = new OperationHeaders(EnumSet.noneOf(OperationContextImpl.ContextFlag.class), null, null, null, null);

    /**
     * Gets a headers object for use by internal calls where the caller knows there will be none of the
     * headers tracked by this class present.
     * @return a headers object
     */
    static OperationHeaders forInternalCall() {
        return INTERNAL;
    }

    static OperationHeaders fromOperation(ModelNode operation) throws OperationFailedException {

        final EnumSet<OperationContextImpl.ContextFlag> contextFlags;
        Integer blockingTimeout = null;
        String warningLevel = null;
        String domainUUID = null;
        AccessMechanism accessMechanism = null;
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

            warningLevel = headers.hasDefined(WARNING_LEVEL) ? headers.get(WARNING_LEVEL).asString() : null;
            domainUUID = headers.hasDefined(DOMAIN_UUID) ? headers.get(DOMAIN_UUID).asString() : null;
            accessMechanism = headers.hasDefined(ACCESS_MECHANISM) ? AccessMechanism.valueOf(headers.get(ACCESS_MECHANISM).asString()) : null;
        } else {
            contextFlags = EnumSet.of(AbstractOperationContext.ContextFlag.ROLLBACK_ON_FAIL);
        }

        return new OperationHeaders(contextFlags, blockingTimeout, warningLevel, domainUUID, accessMechanism);
    }

    static OperationHeaders forBoot(boolean rollbackOnRuntimeFailure) {
        EnumSet<OperationContextImpl.ContextFlag> contextFlags = rollbackOnRuntimeFailure
                ? EnumSet.of(AbstractOperationContext.ContextFlag.ROLLBACK_ON_FAIL)
                : EnumSet.noneOf(OperationContextImpl.ContextFlag.class);
        return new OperationHeaders(contextFlags, null, null, null, null);
    }

    static OperationResponse fromFailure(OperationFailedException ofe) {
        ModelNode responseNode = new ModelNode();
        responseNode.get(OUTCOME).set(FAILED);
        responseNode.get(FAILURE_DESCRIPTION).set(ofe.getFailureDescription());
        return OperationResponse.Factory.createSimple(responseNode);
    }

    private final EnumSet<AbstractOperationContext.ContextFlag> contextFlags;
    private final Integer blockingTimeout;
    private final String warningLevel;
    private final String domainUUID;
    private final AccessMechanism accessMechanism;

    private OperationHeaders(EnumSet<AbstractOperationContext.ContextFlag> contextFlags, Integer blockingTimeout,
                             String warningLevel, String domainUUID, AccessMechanism accessMechanism) {
        this.contextFlags = contextFlags;
        this.blockingTimeout = blockingTimeout;
        this.warningLevel = warningLevel;
        this.domainUUID = domainUUID;
        this.accessMechanism = accessMechanism;
    }

    EnumSet<AbstractOperationContext.ContextFlag> getContextFlags() {
        return contextFlags;
    }

    Integer getBlockingTimeout() {
        return blockingTimeout;
    }

    String getWarningLevel() {
        return warningLevel;
    }

    String getDomainUUID() {
        return domainUUID;
    }

    AccessMechanism getAccessMechanism() {
        return  accessMechanism;
    }
}
