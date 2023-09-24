/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.deployment.transformation.DeploymentTransformer;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getInputStream;

class DeploymentTransformation {
    @SuppressWarnings("deprecation")
    private final DeploymentTransformer deploymentTransformer;

    public DeploymentTransformation() {
        this.deploymentTransformer = loadDeploymentTransformer();
    }

    private static DeploymentTransformer loadDeploymentTransformer() {
        Iterator<DeploymentTransformer> iter = ServiceLoader.load(DeploymentTransformer.class, DeploymentAddHandler.class.getClassLoader()).iterator();
        return iter.hasNext() ? iter.next() : null;
    }

    public InputStream doTransformation(OperationContext context, ModelNode contentItemNode, String name, InputStream in) throws IOException, OperationFailedException {
        if(deploymentTransformer == null) {
            return in;
        }

        try {
            return deploymentTransformer.transform(in, name);
        } catch (RuntimeException t) {
            // Check if the InputStream is already attached to the operation request (as per CONTENT_INPUT_STREAM_INDEX check) and ignore that case
            // as calling getInputStream would of returned the already partially consumed InputStream.
            // Also verify that the thrown exception is the specific WFCORE-5198 `Error code 3`.
            if (!contentItemNode.hasDefined(DeploymentAttributes.CONTENT_INPUT_STREAM_INDEX.getName()) &&
                    t.getCause() != null && t.getCause().getCause() != null &&
                    t.getCause().getCause() instanceof IOException &&
                    t.getCause().getCause().getMessage().contains("during transformation. Error code 3")) {
                ServerLogger.ROOT_LOGGER.tracef(t, "Ignoring transformation error and using original archive %s", name);
                return getInputStream(context, contentItemNode);
            } else {
                throw t;
            }
        }
    }
}