/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

/**
 * A general/parent exception for all deployment related exceptions.
 *
 * @author John E. Bailey
 */
public class DeploymentException extends Exception {
    private static final long serialVersionUID = -4609617514440543548L;

    /**
     * Constructs a {@code DeploymentUnitProcessingException} with no detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public DeploymentException() {
    }

    /**
     * Constructs a {@code DeploymentException} with the specified detail message. The cause is not
     * initialized, and may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public DeploymentException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a {@code DeploymentException} with the specified cause. The detail message is set to:
     * <pre>(cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public DeploymentException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a {@code DeploymentException} with the specified detail message and cause.
     *
     * @param msg   the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public DeploymentException(final String msg, final Throwable cause) {
        super(msg, cause);
    }

}
