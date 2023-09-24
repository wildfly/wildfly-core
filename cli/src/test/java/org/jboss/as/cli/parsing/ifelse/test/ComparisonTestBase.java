/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.ifelse.test;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.handlers.ifelse.ConditionArgument;
import org.jboss.as.cli.handlers.ifelse.IfHandler;
import org.jboss.as.cli.handlers.ifelse.Operation;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.dmr.ModelNode;
import org.junit.BeforeClass;

/**
 *
 * @author Alexey Loubyansky
 */
public class ComparisonTestBase {

    private static DefaultOperationRequestAddress ADDRESS;
    private static DefaultCallbackHandler LINE;
    private static ConditionArgument CONDITION;

    @BeforeClass
    public static void setup() throws Exception {
        ADDRESS = new DefaultOperationRequestAddress();
        LINE = new DefaultCallbackHandler();
        final IfHandler ifHandler = new IfHandler();
        CONDITION = ifHandler.getConditionArgument();
    }

    private MockCommandContext ctx = new MockCommandContext();

    public ComparisonTestBase() {
        super();
    }

    protected void assertTrue(ModelNode node, final String expr)
            throws CommandLineException {
                Operation op = parseExpression(expr);
                final Object value = op.resolveValue(ctx, node);
                org.junit.Assert.assertTrue(op + " " + node, (Boolean)value);
            }

    protected void assertFalse(ModelNode node, final String expr)
            throws CommandLineException {
                Operation op = parseExpression(expr);
                final Object value = op.resolveValue(ctx, node);
                org.junit.Assert.assertFalse((Boolean)value);
            }

    protected Operation parseExpression(String condition) throws CommandFormatException {
        LINE.parse(ADDRESS, "if (" + condition + ") of /a=b:c", ctx);
        return CONDITION.resolveOperation(LINE);
    }
}