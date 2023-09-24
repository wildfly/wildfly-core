/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.common.ResolveExpressionHandler;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Operation that resolves an expression (but not against the vault) and returns the resolved value.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ResolveExpressionOnHostHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "resolve-expression-on-domain";

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("host"))
            .addParameter(ResolveExpressionHandler.EXPRESSION)
            .setReplyType(ModelType.STRING)
            .allowReturnNull()
            .setReadOnly()
            .withFlag(OperationEntry.Flag.DOMAIN_PUSH_TO_SERVERS)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SYSTEM_PROPERTY)
            .build();

    public static final ResolveExpressionOnHostHandler INSTANCE = new ResolveExpressionOnHostHandler();

    private ResolveExpressionOnHostHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Just validate. The real work happens on the servers
        ResolveExpressionHandler.EXPRESSION.validateOperation(operation);
    }
}
