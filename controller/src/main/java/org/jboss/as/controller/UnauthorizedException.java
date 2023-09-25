/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller._private.OperationFailedRuntimeException;

/**
 * {@link OperationFailedRuntimeException} thrown when an operation is not authorized.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class UnauthorizedException extends OperationFailedRuntimeException {

    /**
     * Constructs a {@code OperationFailedException} with the given message.
     * The message is also used as the {@link #getFailureDescription() failure description}.
     * The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param message the description of the failure. Cannot be {@code null}
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
