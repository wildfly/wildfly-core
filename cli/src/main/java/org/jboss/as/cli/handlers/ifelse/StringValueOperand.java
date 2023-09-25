/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class StringValueOperand implements Operand {

    private final ModelNode value;

    public StringValueOperand(String value) {
        checkNotNullParam("value", value);
        ModelNode node;
        try {
            node = ModelNode.fromString(value);
        } catch(IllegalArgumentException e) {
            node = new ModelNode().set(value);
        }
        this.value = node;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.ifelse.Operand#resolveValue(org.jboss.as.cli.CommandContext, org.jboss.dmr.ModelNode)
     */
    @Override
    public Object resolveValue(CommandContext ctx, ModelNode response) throws CommandLineException {
        return value;
    }

    @Override
    public String toString() {
        return value.asString();
    }
}
