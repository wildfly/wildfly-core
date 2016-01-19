/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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