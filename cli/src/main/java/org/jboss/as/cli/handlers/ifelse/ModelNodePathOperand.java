/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class ModelNodePathOperand implements Operand {

    private final String[] path;

    public ModelNodePathOperand(String pathStr) throws CommandFormatException {
        checkNotNullParam("pathStr", pathStr);
        path = pathStr.split("\\.");
        if(path.length == 0) {
            throw new CommandFormatException("The path in the if condition is empty: '" + pathStr + "'");
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.ifelse.Operand#resolveValue(org.jboss.as.cli.CommandContext, org.jboss.dmr.ModelNode)
     */
    @Override
    public Object resolveValue(CommandContext ctx, ModelNode response) throws CommandLineException {
        ModelNode targetValue = response;
        for(String name : path) {
            if(!targetValue.has(name)) {
                return null;
            } else {
                targetValue = targetValue.get(name);
            }
        }
        return targetValue == null ? null : targetValue;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(path[0]);
        for(int i = 1; i < path.length; ++i) {
            buf.append('.').append(path[i]);
        }
        return buf.toString();
    }
}
