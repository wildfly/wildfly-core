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

/**
 * Utility class to support delegation of {@link org.jboss.as.controller.client.ModelControllerClient} calls.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class DelegatingModelControllerClient implements ModelControllerClient {

    /** Provides a delegate for use by the {@code DelegatingModelControllerClient} */
    public interface DelegateProvider {
        /**
         * @throws IllegalStateException if the delegate is not available.
         */
        ModelControllerClient getDelegate() throws IllegalStateException;
    }

    private final DelegateProvider provider;

    public DelegatingModelControllerClient(final ModelControllerClient delegate) {
        this(new DelegateProvider() {
            @Override
            public ModelControllerClient getDelegate() {
                if (delegate == null) {
                    throw new IllegalStateException("The client has been closed");
                }
                return delegate;
            }
        });
    }

    public DelegatingModelControllerClient(DelegateProvider provider) {
        this.provider = provider;
    }

    @Override
    public ModelNode execute(ModelNode operation) throws IOException {
        return provider.getDelegate().execute(operation);
    }

    @Override
    public ModelNode execute(Operation operation) throws IOException {
        return provider.getDelegate().execute(operation);
    }

    @Override
    public ModelNode execute(ModelNode operation, OperationMessageHandler messageHandler) throws IOException {
        return provider.getDelegate().execute(operation, messageHandler);
    }

    @Override
    public ModelNode execute(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        return provider.getDelegate().execute(operation, messageHandler);
    }

    @Override
    public OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        return provider.getDelegate().executeOperation(operation, messageHandler);
    }

    @Override
    public AsyncFuture<ModelNode> executeAsync(ModelNode operation, OperationMessageHandler messageHandler) {
        return provider.getDelegate().executeAsync(operation, messageHandler);
    }

    @Override
    public AsyncFuture<ModelNode> executeAsync(Operation operation, OperationMessageHandler messageHandler) {
        return provider.getDelegate().executeAsync(operation, messageHandler);
    }

    @Override
    public AsyncFuture<OperationResponse> executeOperationAsync(Operation operation, OperationMessageHandler messageHandler) {
        return provider.getDelegate().executeOperationAsync(operation, messageHandler);
    }

    @Override
    public void close() throws IOException {
        try {
            provider.getDelegate().close();
        } catch (IllegalStateException e) {
            // IllegalStateException is ignored, no delegate to close.
        }
    }
}
