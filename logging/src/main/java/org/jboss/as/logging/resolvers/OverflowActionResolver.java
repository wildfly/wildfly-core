/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.resolvers;

import static org.jboss.as.logging.Logging.createOperationFailure;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.handlers.AsyncHandler.OverflowAction;

/**
 * Date: 15.12.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */ /*
 * Resolvers
 */
public class OverflowActionResolver implements ModelNodeResolver<String> {

    public static final OverflowActionResolver INSTANCE = new OverflowActionResolver();

    private OverflowActionResolver() {
    }

    @Override
    public String resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
        try {
            return OverflowAction.valueOf(value.asString()).toString();
        } catch (IllegalArgumentException e) {
            throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidOverflowAction(value.asString()));
        }
    }
}
