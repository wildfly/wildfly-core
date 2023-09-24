/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

/**
 * An exception thrown when a model update fails for some reason.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class UpdateFailedException extends Exception {

    private static final long serialVersionUID = 3601180802244470103L;

    /**
     * Constructs a {@code UpdateFailedException} with no detail message. The cause is not initialized, and may subsequently
     * be initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public UpdateFailedException() {
    }

    /**
     * Constructs a {@code UpdateFailedException} with the specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public UpdateFailedException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a {@code UpdateFailedException} with the specified cause. The detail message is set to:
     * <pre>(cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public UpdateFailedException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a {@code UpdateFailedException} with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public UpdateFailedException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
