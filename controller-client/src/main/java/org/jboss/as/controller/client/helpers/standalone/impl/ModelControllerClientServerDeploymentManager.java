/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client.helpers.standalone.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;

/**
 * {@link org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager} that uses a {@link org.jboss.as.controller.client.ModelControllerClient}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 *
 */
public class ModelControllerClientServerDeploymentManager extends AbstractServerDeploymentManager implements Closeable {

    private final ModelControllerClient client;
    private final boolean closeClient;

    public ModelControllerClientServerDeploymentManager(final ModelControllerClient client, final boolean closeClient) {
        this.client = client;
        this.closeClient = closeClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<ModelNode> executeOperation(Operation operation) {
        return client.executeAsync(operation, null);
    }

    @Override
    public void close() throws IOException {
        if(closeClient) {
            client.close();
        }
    }

}
