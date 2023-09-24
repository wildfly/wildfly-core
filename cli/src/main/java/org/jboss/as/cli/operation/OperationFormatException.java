/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation;

import org.jboss.as.cli.CommandFormatException;

/**
 *
 * @author Alexey Loubyansky
 */
public class OperationFormatException extends CommandFormatException {

    /**
     *
     */
    private static final long serialVersionUID = -3481664048439674648L;

    /**
     * @param message
     * @param cause
     */
    public OperationFormatException(String message, Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    /**
     * @param message
     */
    public OperationFormatException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    /**
     * @param cause
     */
    public OperationFormatException(Throwable cause) {
        super(cause);
        // TODO Auto-generated constructor stub
    }

}
