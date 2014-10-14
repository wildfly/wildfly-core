/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL_HOST_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROCESS_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;


/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ContentRepositoryCleaner {

    private final ContentRepository contentRepository;
    private final ModelControllerClient client;
    private final ControlledProcessStateService controlledProcessStateService;
    private final ScheduledExecutorService scheduledExecutor;

    private long cleanInterval = 0L;
    private volatile boolean enabled;
    private ScheduledFuture<?> cleanTask;

    private final ContentRepositoryCleanerTask cleanRunnable = new ContentRepositoryCleanerTask();

    private class ContentRepositoryCleanerTask implements Runnable {

        @Override
        public void run() {
            clean();
        }
    }

    public ContentRepositoryCleaner(ContentRepository contentRepository, ModelControllerClient client,
            ControlledProcessStateService controlledProcessStateService, ScheduledExecutorService scheduledExecutor,
            long interval) {
        this.contentRepository = contentRepository;
        this.controlledProcessStateService = controlledProcessStateService;
        this.client = client;
        this.scheduledExecutor = scheduledExecutor;
        this.enabled = true;
        this.cleanInterval = interval;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getCleanInterval() {
        return cleanInterval;
    }

    /**
     * Invoke with the object monitor held
     */
    private void cancelScan() {
        if (cleanTask != null) {
            cleanTask.cancel(false);
            cleanTask = null;
        }
        try {
            client.close();
        } catch (IOException ex) {
            ServerLogger.ROOT_LOGGER.error("Error stopping content repository cleaner", ex);
        }
    }

    public synchronized void startScan() {
        if (enabled) {
            cleanTask = scheduledExecutor.scheduleWithFixedDelay(cleanRunnable, cleanInterval, cleanInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void stopScan() {
        this.enabled = false;
        cancelScan();
    }

    void clean() {
        if (controlledProcessStateService.getCurrentState() == ControlledProcessState.State.RUNNING) {
            Map<String, Set<ContentReference>> allReferences = listAllAvailableDeployments();
            Set<ContentReference> references = new HashSet<>();
            for (Set<ContentReference> allReferencedContents : allReferences.values()) {
                references.addAll(allReferencedContents);
            }
            contentRepository.syncReferences(references);
        }
    }

    /**
     * List all contents (deplyments and rollout plans).
     * @return all contents (deplyments and rollout plans).
     */
    private Map<String, ContentReference> listAllContents() {
        Map<String, ContentReference> contents = new HashMap<>();
        final ModelNode deploymentNames = readChildrenNames(PathAddress.EMPTY_ADDRESS, DEPLOYMENT);
        if (deploymentNames.isDefined()) {
            for (ModelNode deploymentNode : deploymentNames.asList()) {
                String deploymentName = deploymentNode.asString();
                ContentReference reference = readContentReference(PathAddress.EMPTY_ADDRESS,DEPLOYMENT, deploymentName);
                if (reference != null) {
                    contents.put(deploymentName, reference);
                }
            }
        }
        final ModelNode deploymentOverlayNames = readChildrenNames(PathAddress.EMPTY_ADDRESS, DEPLOYMENT_OVERLAY);
        if (deploymentOverlayNames.isDefined()) {
            for (ModelNode deploymentOverlayNode : deploymentOverlayNames.asList()) {
                String deploymentOverlayName = deploymentOverlayNode.asString();
                PathAddress overlayAddress = PathAddress.EMPTY_ADDRESS.append(DEPLOYMENT_OVERLAY, deploymentOverlayName);
                ModelNode contentNamesResult = readChildrenNames(overlayAddress, CONTENT);
                if (contentNamesResult.isDefined()) {
                    for (ModelNode contentNameNode : contentNamesResult.asList()) {
                        String contentName = contentNameNode.asString();
                        PathAddress contentParentAddress = PathAddress.pathAddress(overlayAddress, PathElement.pathElement(CONTENT, contentName));
                        ModelNode hashResult = readAttribute(contentParentAddress.toModelNode(), CONTENT);
                        if (hashResult.isDefined()) {
                            ContentReference reference = ModelContentReference.fromDeploymentAddress(contentParentAddress, hashResult.asBytes()).toReference();
                            contents.put(deploymentOverlayName, reference);
                        }
                    }
                }
            }
        }
        PathAddress rolloutPlansAddress = PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        final ModelNode rolloutPlansHash = readAttribute(rolloutPlansAddress.toModelNode(), HASH);
        if (rolloutPlansHash.isDefined()) {
            byte[] hash = rolloutPlansHash.asBytes();
            contents.put(ROLLOUT_PLANS, ModelContentReference.fromDeploymentAddress(rolloutPlansAddress, hash).toReference());
        }
        return contents;
    }

    private ContentReference readContentReference(PathAddress address, String type, String name) {
        PathAddress contentParentAddress = PathAddress.pathAddress(address, PathElement.pathElement(type, name));
        ModelNode hashResult = readAttribute(contentParentAddress.toModelNode(), CONTENT);
        if (hashResult.isDefined()) {
            for (ModelNode attribute : hashResult.asList()) {
                if (attribute.has(HASH)) {
                    return ModelContentReference.fromDeploymentAddress(contentParentAddress, attribute.get(HASH).asBytes()).toReference();
                }
            }
        }
        return null;
    }

    private Map<String, Set<ContentReference>> listAllAvailableDeployments() {
        Map<String, ContentReference> deployments = listAllContents();
        Map<String, Set<ContentReference>> allDeployments = new HashMap<String, Set<ContentReference>>();
        for (Map.Entry<String, ContentReference> deployment : deployments.entrySet()) {
            Set<ContentReference> references = new HashSet<>();
            references.add(deployment.getValue());
            allDeployments.put(deployment.getKey(), references);
        }
        ModelNode hostNode = readAttribute(PathAddress.EMPTY_ADDRESS.toModelNode(), LOCAL_HOST_NAME);
        if (hostNode.isDefined() && hostNode.hasDefined(LOCAL_HOST_NAME)) {
            String hostName = hostNode.get(LOCAL_HOST_NAME).asString();
            PathAddress hostAddress = PathAddress.pathAddress(HOST, hostName);
            if (isHostRunning(hostName)) {
                final ModelNode serverResult = readChildrenNames(hostAddress, SERVER);
                if (serverResult.isDefined()) {
                    for (ModelNode serverNode : serverResult.asList()) {
                        String serverName = serverNode.asString();
                        if (isServerRunning(hostName, serverName)) {
                            PathAddress serverAddress = hostAddress.append(SERVER, serverName);
                            ModelNode serverGroupNode = readAttribute(serverAddress.toModelNode(), SERVER_GROUP);
                            if (serverGroupNode.isDefined()) {
                                String serverGroupName = serverGroupNode.asString();
                                PathAddress serverGroupAddress = PathAddress.pathAddress(PathAddress.EMPTY_ADDRESS, PathElement.pathElement(SERVER_GROUP, serverGroupName));
                                ModelNode deploymentResult = readChildrenNames(serverGroupAddress, DEPLOYMENT);
                                if (deploymentResult.isDefined()) {
                                    for (ModelNode deploymentNode : deploymentResult.asList()) {
                                        String deploymentName = deploymentNode.asString();
                                        if (deployments.containsKey(deploymentName)) {
                                            ContentReference reference = readContentReference(serverGroupAddress, DEPLOYMENT, deploymentName);
                                            if (reference != null) {
                                                allDeployments.get(deploymentName).add(reference);
                                            }
                                        }
                                    }
                                }
                            }
                            ModelNode serverDeploymentResult = readChildrenNames(serverAddress, DEPLOYMENT);
                            if (serverDeploymentResult.isDefined()) {
                                for (ModelNode serverDeploymentNode : serverDeploymentResult.asList()) {
                                    String deploymentName = serverDeploymentNode.asString();
                                    if (deployments.containsKey(deploymentName)) {
                                        ContentReference reference = readContentReference(serverAddress, DEPLOYMENT, deploymentName);
                                        if (reference != null) {
                                            allDeployments.get(deploymentName).add(reference);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return allDeployments;
    }

    private boolean isHostRunning(String hostName) {
        PathAddress hostAddress = PathAddress.pathAddress(HOST, hostName);
        ModelNode hostStateResult = readAttribute(hostAddress.toModelNode(), HOST_STATE);
        return hostStateResult.hasDefined(HOST_STATE) && ControlledProcessState.State.RUNNING.toString().equals(hostStateResult.get(HOST_STATE).asString());
    }

    private boolean isServerRunning(String hostName, String serverName) {
        PathAddress serverAddress = PathAddress.pathAddress(HOST, hostName).append(SERVER, serverName);
        ModelNode serverStateResult = readAttribute(serverAddress.toModelNode(), PROCESS_STATE);
        return serverStateResult.hasDefined(PROCESS_STATE) && ControlledProcessState.State.RUNNING.toString().equals(serverStateResult.get(PROCESS_STATE).asString());
    }

    private ModelNode readChildrenNames(final PathAddress address, final String type) {
        final ModelNode op = new ModelNode();
        op.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        op.get(OP_ADDR).set(address.toModelNode());
        op.get(CHILD_TYPE).set(type);
        try {
            ModelNode response = client.execute(op);
            if (!FAILED.equals(response.get(OUTCOME).asString()) && response.get(RESULT).isDefined()) {
                return response.get(RESULT);
            }
        } catch (IOException e) {
            ServerLogger.ROOT_LOGGER.error("Error stopping content repository cleaner", e);
            return new ModelNode();
        }
        return new ModelNode();
    }

    private ModelNode readAttribute(ModelNode address, String attributeName) {
        try {
            ModelNode response = client.execute(Operations.createReadAttributeOperation(address, attributeName));
            if (!FAILED.equals(response.get(OUTCOME).asString()) && response.get(RESULT).isDefined()) {
                return response.get(RESULT);
            }
        } catch (IOException e) {
            ServerLogger.ROOT_LOGGER.error("Error stopping content repository cleaner", e);
            return new ModelNode();
        }
        return new ModelNode();
    }
}
