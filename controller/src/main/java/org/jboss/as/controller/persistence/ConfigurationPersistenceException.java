/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

/**
 * An exception relating to a configuration persistence problem.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class ConfigurationPersistenceException extends Exception {

    private static final long serialVersionUID = 3004052407520201440L;

    /**
     * Constructs a {@code ConfigurationPersistenceException} with no detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public ConfigurationPersistenceException() {
    }

    /**
     * Constructs a {@code ConfigurationPersistenceException} with the specified detail message. The cause is not
     * initialized, and may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public ConfigurationPersistenceException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a {@code ConfigurationPersistenceException} with the specified cause. The detail message is set to:
     * <pre>(cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public ConfigurationPersistenceException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a {@code ConfigurationPersistenceException} with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public ConfigurationPersistenceException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
