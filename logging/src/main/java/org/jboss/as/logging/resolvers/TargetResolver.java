/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.resolvers;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.logging.handlers.Target;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.handlers.ConsoleHandler;

/**
 * Date: 15.12.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TargetResolver implements ModelNodeResolver<String> {

    public static final TargetResolver INSTANCE = new TargetResolver();

    private TargetResolver() {
    }

    @Override
    public String resolveValue(final OperationContext context, final ModelNode value) {
        final String result;
        switch (Target.fromString(value.asString())) {
            case SYSTEM_ERR: {
                result = ConsoleHandler.Target.SYSTEM_ERR.name();
                break;
            }
            case SYSTEM_OUT: {
                result = ConsoleHandler.Target.SYSTEM_OUT.name();
                break;
            }
            case CONSOLE: {
                result = ConsoleHandler.Target.CONSOLE.name();
                break;
            }
            default:
                result = null;
                break;
        }
        return result;
    }
}
