/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

/**
 * An exception which is thrown when deployment unit processing fails.  This can occur as a result of a failure
 * to parse a descriptor, an error transforming a descriptor, an error preparing a deployment item, or other causes.
 */
public class DeploymentUnitProcessingException extends DeploymentException {

    private static final long serialVersionUID = -3242784227234412566L;

    /**
     * Constructs a {@code DeploymentUnitProcessingException} with the specified detail message. The cause is not
     * initialized, and may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public DeploymentUnitProcessingException(final String msg) {
        super(msg);
    }

    /**
     * Constructs a {@code DeploymentUnitProcessingException} with the specified cause. The detail message is set to:
     * <pre>(cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public DeploymentUnitProcessingException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a {@code DeploymentUnitProcessingException} with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public DeploymentUnitProcessingException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
