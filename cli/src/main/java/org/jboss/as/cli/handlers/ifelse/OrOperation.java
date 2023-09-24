/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.dmr.ModelNode;

/**
*
* @author Alexey Loubyansky
*/
class OrOperation extends BaseOperation {

    static final String SYMBOL = "||";

    OrOperation() {
        super(SYMBOL, 2);
    }

    @Override
    public Object resolveValue(CommandContext ctx, ModelNode response) throws CommandLineException {
        for(Operand operand : getOperands()) {
            final Object value = operand.resolveValue(ctx, response);
            if(!(value instanceof Boolean)) {
                throw new CommandFormatException("Expected boolean value from " + operand + " but received " + value);
            }
            if (((Boolean)value)) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    @Override
    public boolean allowsMoreArguments() {
        return true;
    }
}