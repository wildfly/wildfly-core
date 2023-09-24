/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;
import org.wildfly.common.Assert;
import org.wildfly.common.context.Contextual;

/**
 * A {@linkplain ModelControllerClient client} which wraps invocations of the delegate client in the provided
 * {@linkplain Contextual context}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public final class ContextualModelControllerClient implements ModelControllerClient {
    private final ModelControllerClient delegate;
    private final Contextual<?> context;

    /**
     * Creates a client which uses the supplied {@linkplain Contextual context}.
     *
     * @param delegate the delegate client
     * @param context  the context used to execute operations
     */
    public ContextualModelControllerClient(final ModelControllerClient delegate, final Contextual<?> context) {
        this.delegate = Assert.checkNotNullParam("delegate", delegate);
        this.context = Assert.checkNotNullParam("context", context);
    }

    @Override
    public OperationResponse executeOperation(final Operation operation, final OperationMessageHandler messageHandler) throws IOException {
        return context.runExFunction(o -> delegate.executeOperation(operation, messageHandler), null);
    }

    @Override
    public AsyncFuture<ModelNode> executeAsync(final Operation operation, final OperationMessageHandler messageHandler) {
        return context.runExFunction(o -> delegate.executeAsync(operation, messageHandler), null);
    }

    @Override
    public AsyncFuture<OperationResponse> executeOperationAsync(final Operation operation, final OperationMessageHandler messageHandler) {
        return context.runExFunction(o -> delegate.executeOperationAsync(operation, messageHandler), null);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
