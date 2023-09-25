/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing;

/**
 * @author Alexey Loubyansky
 *
 */
public class UnresolvedVariableException extends UnresolvedExpressionException {

    private static final long serialVersionUID = 1L;

    /**
     * @param variable  variable name
     */
    public UnresolvedVariableException(String variable) {
        super(variable, "Unrecognized variable " + variable);
    }

    /**
     * @param expr  variable name
     * @param msg  error message
     */
    public UnresolvedVariableException(String expr, String msg) {
        super(expr, msg);
    }

    /**
     * @param expr  variable name
     * @param msg  error message
     * @param t  cause of the problem
     */
    public UnresolvedVariableException(String expr, String msg, Throwable t) {
        super(expr, msg, t);
    }

}
