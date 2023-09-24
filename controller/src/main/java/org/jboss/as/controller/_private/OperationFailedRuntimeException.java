/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller._private;

import org.jboss.as.controller.OperationClientException;
import org.jboss.dmr.ModelNode;

/**
 * Runtime exception indicating an operation has failed due to a client mistake (e.g. an operation with
 * invalid parameters was invoked.) Should not be used to report server failures.
 * <p>
 * This is a {@link RuntimeException} variant of {@link org.jboss.as.controller.OperationFailedException} and is intended
 * for use in cases where the semantics of {@link org.jboss.as.controller.OperationFailedException} are desired but an
 * API does not allow a checked exception to be thrown. See https://issues.jboss.org/browse/AS7-2905 .
 * </p>
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class OperationFailedRuntimeException extends RuntimeException implements OperationClientException {

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
    public OperationFailedRuntimeException(final String message) {
        super(message);
        assert message != null : "message is null";
        failureDescription = new ModelNode(message);
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
