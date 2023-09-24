/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;

/**
 * Base class for mock ModelController impls used in tests.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public abstract class MockModelController implements ModelController {

    @Override
    public OperationResponse execute(Operation operation, OperationMessageHandler handler, OperationTransactionControl control) {
        ModelNode simpleResponse = execute(operation.getOperation(), handler, control, operation);
        return OperationResponse.Factory.createSimple(simpleResponse);
    }
}
