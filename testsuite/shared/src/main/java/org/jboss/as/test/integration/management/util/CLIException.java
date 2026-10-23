/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.util;

/**
 *
 * @author <a href="dpospisi@redhat.com">Dominik Pospisil</a>
 */
public class CLIException extends RuntimeException {

    /**
     * Creates a new instance of
     * <code>CLIException</code> without detail message.
     */
    public CLIException() {
    }

    /**
     * Constructs an instance of
     * <code>CLIException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public CLIException(String msg) {
        super(msg);
    }
}
