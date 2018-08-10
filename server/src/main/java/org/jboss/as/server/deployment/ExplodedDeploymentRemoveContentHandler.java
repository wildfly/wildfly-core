/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.repository.PathUtil.deleteRecursively;
import static org.jboss.as.server.Services.JBOSS_SERVER_EXECUTOR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.REMOVED_PATHS;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.addFlushHandler;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.getContentItem;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isArchive;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isManaged;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.createFailureException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.as.repository.PathUtil;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the "remove-content" operation over an exploded managed deployment.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ExplodedDeploymentRemoveContentHandler implements OperationStepHandler {

    private final ContentRepository contentRepository;
    private final ServerEnvironment serverEnvironment;

    public ExplodedDeploymentRemoveContentHandler(final ContentRepository contentRepository, final ServerEnvironment serverEnvironment) {
        assert contentRepository != null : "Null contentRepository";
        this.contentRepository = contentRepository;
        this.serverEnvironment = serverEnvironment;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.getProcessType() == ProcessType.SELF_CONTAINED) {
            throw ServerLogger.ROOT_LOGGER.cannotRemoveContentFromSelfContainedServer();
        }
        final Resource deploymentResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        ModelNode contentItemNode = getContentItem(deploymentResource);
        // Validate this op is available
        if (!isManaged(contentItemNode)) {
            throw ServerLogger.ROOT_LOGGER.cannotRemoveContentFromUnmanagedDeployment();
        } else if (isArchive(contentItemNode)) {
            throw ServerLogger.ROOT_LOGGER.cannotRemoveContentFromUnexplodedDeployment();
        }
        final String managementName = context.getCurrentAddress().getLastElement().getValue();
        final PathAddress address = PathAddress.pathAddress(DEPLOYMENT, managementName);
        final byte[] oldHash = CONTENT_HASH.resolveModelAttribute(context, contentItemNode).asBytes();
        final List<String> paths = REMOVED_PATHS.unwrap(context, operation);
        final byte[] newHash;
        if (operation.hasDefined(CONTENT_HASH.getName())) {
            newHash = DeploymentHandlerUtil.addFromHash(contentRepository, operation, managementName, address, context);
        } else {
            try {
                newHash = contentRepository.removeContentFromExploded(oldHash, paths);
            } catch (ExplodedContentException ex) {
                throw createFailureException(ex.toString());
            }
        }
        contentItemNode.get(CONTENT_HASH.getName()).set(newHash);
        if (!paths.isEmpty() && ENABLED.resolveModelAttribute(context, deploymentResource.getModel()).asBoolean()) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    try {
                        ExecutorService executor = (ExecutorService) context.getServiceRegistry(false).getRequiredService(JBOSS_SERVER_EXECUTOR).getValue();
                        CountDownLatch latch = delete(executor, paths, managementName);
                        if (latch != null) {
                            try {
                                if (!latch.await(60, TimeUnit.SECONDS)) {
                                    return;
                                }
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw createFailureException(ex.toString());
                            }
                        }
                    } catch (IOException ex) {
                        throw createFailureException(ex.toString());
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
        addFlushHandler(context, contentRepository, new OperationContext.ResultHandler() {
            @Override
            public void handleResult(ResultAction resultAction, OperationContext context, ModelNode operation) {
                if (resultAction == ResultAction.KEEP) {
                    if (oldHash != null  && (newHash == null || !Arrays.equals(oldHash, newHash))) {
                        // The old content is no longer used; clean from repos
                        contentRepository.removeContent(ModelContentReference.fromModelAddress(address, oldHash));
                    }
                    if (newHash != null) {
                        contentRepository.addContentReference(ModelContentReference.fromModelAddress(address, newHash));
                    }
                } else if (newHash != null && (oldHash == null || !Arrays.equals(oldHash, newHash))) {
                    // Due to rollback, the new content isn't used; clean from repos
                    contentRepository.removeContent(ModelContentReference.fromModelAddress(address, newHash));
                }
            }
        });
    }

    private CountDownLatch delete(ExecutorService executor, List<String> paths, String managementName) throws IOException {
        final CountDownLatch result;
        Path runtimeDeployedPath = DeploymentHandlerUtil.getExplodedDeploymentRoot(serverEnvironment, managementName);
        if (Files.exists(runtimeDeployedPath)) {
            result = new CountDownLatch(1);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        for (String path : paths) {
                            try {
                                deleteRecursively(PathUtil.resolveSecurely(runtimeDeployedPath, path));
                            } catch (IOException ex) {
                                ServerLogger.DEPLOYMENT_LOGGER.couldNotDeleteFile(ex, path, managementName);
                            }
                        }
                    } finally {
                        result.countDown();
                    }
                }
            };
            executor.submit(r);
        } else {
            result = null;
        }
        return result;
    }
}
