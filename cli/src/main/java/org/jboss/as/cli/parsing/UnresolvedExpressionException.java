/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing;

import org.jboss.as.cli.CommandFormatException;


/**
 * @author Alexey Loubyansky
 *
 */
public class UnresolvedExpressionException extends CommandFormatException {

    private static final long serialVersionUID = 1L;

    private final String expr;

    public UnresolvedExpressionException(String variable) {
        this(variable, "Unrecognized expression " + variable);
    }

    public UnresolvedExpressionException(String expr, String msg) {
        super(msg);
        this.expr = expr;
    }

    public UnresolvedExpressionException(String expr, String msg, Throwable t) {
        super(msg, t);
        this.expr = expr;
    }

    public String getExpression() {
        return expr;
    }
}
