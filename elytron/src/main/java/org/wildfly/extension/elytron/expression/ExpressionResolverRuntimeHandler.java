/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron.expression;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;

/**
 * Utility to allow the add handler for the /subsystem=elytron/expression=encryption resource
 * to initialize the {@link ElytronExpressionResolver}.
 */
public final class ExpressionResolverRuntimeHandler {

    public static void initializeResolver(OperationContext context) throws OperationFailedException {
        ElytronExpressionResolver resolver = context.getCapabilityRuntimeAPI("org.wildfly.security.expression-resolver", ElytronExpressionResolver.class);
        resolver.ensureInitialised(null, context);
    }
}
