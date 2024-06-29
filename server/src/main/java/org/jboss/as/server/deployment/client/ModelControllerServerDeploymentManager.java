/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.client;

import java.io.IOException;
import java.util.concurrent.Future;

import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.standalone.impl.AbstractServerDeploymentManager;
import org.jboss.dmr.ModelNode;

/**
 * {@link org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager} the uses a {@link org.jboss.as.controller.ModelController}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 *
 * @deprecated Use {@link org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager.Factory}
 */
@Deprecated(forRemoval = true)
public class ModelControllerServerDeploymentManager extends AbstractServerDeploymentManager {

    private final ModelControllerClient client;

    public ModelControllerServerDeploymentManager(final LocalModelControllerClient client) {
        this.client = client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<ModelNode> executeOperation(Operation executionContext) {
        return client.executeAsync(executionContext, null);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
