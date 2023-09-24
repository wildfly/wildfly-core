/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.dmr.ModelNode;


/**
*
* @author Alexey Loubyansky
*/
abstract class ComparisonOperation extends BaseOperation {
    ComparisonOperation(String name) {
        super(name, 8);
    }

    @Override
    public Object resolveValue(CommandContext ctx, ModelNode response) throws CommandLineException {
        final List<Operand> operands = getOperands();
        if(operands.isEmpty()) {
            throw new CommandLineException(getName() + " has no operands.");
        }
        if(operands.size() != 2) {
            throw new CommandLineException(getName() + " expects 2 operands but got " + operands.size());
        }
        final Object left = operands.get(0).resolveValue(ctx, response);
        if(left == null) {
            return false;
        }
        final Object right = operands.get(1).resolveValue(ctx, response);
        if(right == null) {
            return false;
        }
        if(!(left instanceof ModelNode) || !(right instanceof ModelNode)) {
            throw new CommandLineException("Operands aren't instances of org.jboss.dmr.ModelNode: " +
                left.getClass().getName() + ", " + right.getClass().getName());
        }
        return compare(left, right);
    }

    protected abstract boolean compare(Object left, Object right) throws CommandLineException;
}