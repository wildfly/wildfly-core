/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller._private;

import java.util.concurrent.CancellationException;

import org.jboss.as.controller.OperationClientException;
import org.jboss.dmr.ModelNode;

/**
 * {@link CancellationException} variant that implements {@link OperationClientException}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class OperationCancellationException extends CancellationException implements OperationClientException {

    private static final long serialVersionUID = 0;

    public OperationCancellationException(String message) {
        super(message);
        assert message != null : "message is null";
    }

    @Override
    public ModelNode getFailureDescription() {
        return new ModelNode(getMessage());
    }
}
