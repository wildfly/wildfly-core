/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.logging.deployments;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;

/**
 * A DUP which is registered in the {@linkplain org.jboss.as.server.deployment.Phase#STRUCTURE structure phase} to
 * ensure {@link #undeploy(DeploymentUnit)} is processed last. The {@link #deploy(DeploymentPhaseContext)} for this
 * DUP does nothing.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingCleanupDeploymentProcessor implements DeploymentUnitProcessor {
    private static final AttachmentKey<Set<AutoCloseable>> RESOURCES_TO_CLOSE = AttachmentKey.create(Set.class);

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) {
        // Nothing to do during deploy
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
        final Set<AutoCloseable> resources = context.removeAttachment(RESOURCES_TO_CLOSE);
        if (resources != null) {
            for (AutoCloseable resource : resources) {
                try {
                    LoggingLogger.ROOT_LOGGER.tracef("Closing %s", resource);
                    resource.close();
                } catch (Exception e) {
                    LoggingLogger.ROOT_LOGGER.failedToCloseResource(e, resource);
                }
            }
        }
    }

    /**
     * Adds a resource to be cleaned when the deployment is being undeployed.
     *
     * @param deploymentUnit the deployment
     * @param resource       the resource to add
     */
    static synchronized void addResource(final DeploymentUnit deploymentUnit, final AutoCloseable resource) {
        Set<AutoCloseable> resources = deploymentUnit.getAttachment(RESOURCES_TO_CLOSE);
        if (resources == null) {
            resources = Collections.newSetFromMap(new IdentityHashMap<>());
            deploymentUnit.putAttachment(RESOURCES_TO_CLOSE, resources);
        }
        resources.add(resource);
    }
}
