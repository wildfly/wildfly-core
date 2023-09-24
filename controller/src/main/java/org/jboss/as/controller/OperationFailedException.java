/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * Exception indicating an operation has failed due to a client mistake (e.g. an operation with
 * invalid parameters was invoked.) Should not be used to report server failures.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class OperationFailedException extends Exception implements OperationClientException {

    private final ModelNode failureDescription;

    private static final long serialVersionUID = -1896884563520054972L;

    /**
     * Constructs a {@code OperationFailedException} with the given message.
     * The message is also used as the {@link #getFailureDescription() failure description}.
     * The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param message the description of the failure. Cannot be {@code null}
     */
    public OperationFailedException(final String message) {
        this(message, new ModelNode(message));
    }

    /**
     * Constructs a {@code OperationFailedException} with the specified cause and message.
     * The message is also used as the {@link #getFailureDescription() failure description}.
     *
     * @param message the description of the failure. Cannot be {@code null}
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public OperationFailedException(final String message, final Throwable cause) {
        this(message, cause, new ModelNode(message));
    }

    /**
     * Constructs a {@code OperationFailedException} with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     * @param description the description of the failure. Cannot be {@code null}
     */
    public OperationFailedException(final String msg, final ModelNode description) {
        super(msg);
        assert description != null : "description is null";
        failureDescription = description;
    }

    /**
     * Constructs a {@code OperationFailedException} with the specified cause.
     * The {@link Throwable#getMessage() cause's message} is also used as the
     * {@link #getFailureDescription() failure description}.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method). Cannot be {@code null}
     */
    public OperationFailedException(final Throwable cause) {
        this(cause.getMessage(), cause);
    }

    /**
     * Constructs a {@code OperationFailedException} with the specified cause. The detail message is set to:
     * <pre>(cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     * @param description the description of the failure. Cannot be {@code null}
     */
    public OperationFailedException(final Throwable cause, final ModelNode description) {
        super(cause);
        assert description != null : "description is null";
        failureDescription = description;
    }

    /**
     * Constructs a {@code OperationFailedException} with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     * @param description the description of the failure. Cannot be {@code null}
     */
    public OperationFailedException(final String msg, final Throwable cause, final ModelNode description) {
        super(msg, cause);
        assert description != null : "description is null";
        failureDescription = description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModelNode getFailureDescription() {
        return failureDescription;
    }

    @Override
    public String toString() {
        return super.toString() + " [ " + failureDescription + " ]";
    }
}
