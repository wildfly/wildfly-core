/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
        ModelControllerClient getDelegate();
    }

    private final DelegateProvider provider;

    public DelegatingModelControllerClient(final ModelControllerClient delegate) {
        this(new DelegateProvider() {
            @Override
            public ModelControllerClient getDelegate() {
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
        provider.getDelegate().close();
    }
}
