/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.resolvers;

import static org.jboss.as.logging.Logging.createOperationFailure;

import java.util.logging.Level;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;

/**
 * Date: 15.12.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LevelResolver implements ModelNodeResolver<String> {

    public static final LevelResolver INSTANCE = new LevelResolver();

    private LevelResolver() {
    }


    @Override
    public String resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
        try {
            Level.parse(value.asString());
            return value.asString();
        } catch (IllegalArgumentException e) {
            throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidLogLevel(value.asString()));
        }
    }
}
