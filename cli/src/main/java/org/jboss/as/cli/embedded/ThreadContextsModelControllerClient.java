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
