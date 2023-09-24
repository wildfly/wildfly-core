/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

/**
 * @author Alexey Loubyansky
 *
 */
public class CommandLineException extends Exception {

    private static final long serialVersionUID = 423938082439473323L;

    public CommandLineException(String message, Throwable cause) {
        super(message, cause);
    }

    public CommandLineException(String message) {
        super(message);
    }

    public CommandLineException(Throwable cause) {
        super(cause);
    }
}
