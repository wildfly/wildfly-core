/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing;

/**
 * @author Alexey Loubyansky
 *
 */
public class CommandSubstitutionException extends UnresolvedExpressionException {

    private static final long serialVersionUID = 1L;

    /**
     * @param expr  expression which failed
     */
    public CommandSubstitutionException(String expr) {
        super(expr);
    }

    /**
     * @param expr  expression which failed
     * @param msg  error message
     */
    public CommandSubstitutionException(String expr, String msg) {
        super(expr, msg);
    }

    /**
     * @param expr  expression which failed
     * @param msg  error message
     * @param t  the cause of the problem
     */
    public CommandSubstitutionException(String expr, String msg, Throwable t) {
        super(expr, msg, t);
    }
}
