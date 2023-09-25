/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

/**
 * @author Alexey Loubyansky
 *
 */
public class CliInitializationException extends CommandLineException {

    private static final long serialVersionUID = -5802389813870206943L;

    /**
     * @param message
     * @param cause
     */
    public CliInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     */
    public CliInitializationException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public CliInitializationException(Throwable cause) {
        super(cause);
    }
}
