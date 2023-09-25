/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;

/**
 * {@link org.jboss.as.controller.client.ModelControllerClient} that uses a
 * {@link ThreadLocalContextSelector} to ensure that
 * a given {@link org.jboss.stdio.StdioContext} is in effect during execution of
 * synchronous calls.
*
* @author Brian Stansberry (c) 2015 Red Hat Inc.
*/
class ThreadContextsModelControllerClient implements ModelControllerClient {

    private final ModelControllerClient delegate;
    private final ThreadLocalContextSelector contextSelector;

    ThreadContextsModelControllerClient(ModelControllerClient delegate,
                                        ThreadLocalContextSelector contextSelector) {
        assert delegate != null;
        assert contextSelector != null;
        this.delegate = delegate;
        this.contextSelector = contextSelector;
    }

    @Override
    public ModelNode execute(ModelNode operation) throws IOException {
        Contexts existing = contextSelector.pushLocal();
        try {
            return delegate.execute(operation);
        } finally {
            contextSelector.restore(existing);
        }
    }

    @Override
    public ModelNode execute(Operation operation) throws IOException {
        Contexts existing = contextSelector.pushLocal();
        try {
            return delegate.execute(operation);
        } finally {
            contextSelector.restore(existing);
        }
    }

    @Override
    public ModelNode execute(ModelNode operation, OperationMessageHandler messageHandler) throws IOException {
        Contexts existing = contextSelector.pushLocal();
        try {
            return delegate.execute(operation, messageHandler);
        } finally {
            contextSelector.restore(existing);
        }
    }

    @Override
    public ModelNode execute(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        Contexts existing = contextSelector.pushLocal();
        try {
            return delegate.execute(operation, messageHandler);
        } finally {
            contextSelector.restore(existing);
        }
    }

    @Override
    public OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        Contexts existing = contextSelector.pushLocal();
        try {
            return delegate.executeOperation(operation, messageHandler);
        } finally {
            contextSelector.restore(existing);
        }
    }

    @Override
    public AsyncFuture<ModelNode> executeAsync(ModelNode operation, OperationMessageHandler messageHandler) {
        Contexts existing = contextSelector.pushLocal();
        try {
            return delegate.executeAsync(operation, messageHandler);
        } finally {
            contextSelector.restore(existing);
        }
    }

    @Override
    public AsyncFuture<ModelNode> executeAsync(Operation operation, OperationMessageHandler messageHandler) {
        Contexts existing = contextSelector.pushLocal();
        try {
            return delegate.executeAsync(operation, messageHandler);
        } finally {
            contextSelector.restore(existing);
        }
    }

    @Override
    public AsyncFuture<OperationResponse> executeOperationAsync(Operation operation, OperationMessageHandler messageHandler) {
        Contexts existing = contextSelector.pushLocal();
        try {
            return delegate.executeOperationAsync(operation, messageHandler);
        } finally {
            contextSelector.restore(existing);
        }
    }

    @Override
    public void close() throws IOException {
        Contexts existing = contextSelector.pushLocal();
        try {
            delegate.close();
        } finally {
            contextSelector.restore(existing);
        }
    }
}
