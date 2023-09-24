/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;

/**
 * Called by batch once composite response is received.
 * @author jdenise@redhat.com
 */
public interface ResponseHandler {
    /**
     * Handle the passed step.
     * @param step The current step.
     * @param response The operation that contains the step.
     * @throws CommandLineException
     */
    void handleResponse(ModelNode step, OperationResponse response) throws CommandLineException;
}
