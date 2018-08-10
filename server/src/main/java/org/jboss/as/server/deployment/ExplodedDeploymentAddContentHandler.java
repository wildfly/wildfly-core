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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.server.Services.JBOSS_SERVER_EXECUTOR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_ARCHIVE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_PARAM_ALL_EXPLODED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.OVERWRITE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.TARGET_PATH;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.addFlushHandler;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.getContentItem;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isArchive;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isManaged;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.createFailureException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.jboss.as.repository.ExplodedContent;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the "add-content" operation over an exploded managed deployment.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ExplodedDeploymentAddContentHandler implements OperationStepHandler {

    private final ContentRepository contentRepository;
    private final ServerEnvironment serverEnvironment;

    public ExplodedDeploymentAddContentHandler(final ContentRepository contentRepository,
            final ServerEnvironment serverEnvironment) {
        assert contentRepository != null : "Null contentRepository";
        this.contentRepository = contentRepository;
        this.serverEnvironment = serverEnvironment;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.getProcessType() == ProcessType.SELF_CONTAINED) {
            throw ServerLogger.ROOT_LOGGER.cannotAddContentToSelfContainedServer();
        }
        final Resource deploymentResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        ModelNode contentItemNode = getContentItem(deploymentResource);
        // Validate this op is available
        if (!isManaged(contentItemNode)) {
            throw ServerLogger.ROOT_LOGGER.cannotAddContentToUnmanagedDeployment();
        } else if (isArchive(contentItemNode)) {
            throw ServerLogger.ROOT_LOGGER.cannotAddContentToUnexplodedDeployment();
        }
        final String managementName = context.getCurrentAddress().getLastElement().getValue();
        final PathAddress address = PathAddress.pathAddress(DEPLOYMENT, managementName);
        final byte[] oldHash = CONTENT_HASH.resolveModelAttribute(context, contentItemNode).asBytes();
        final boolean overwrite = OVERWRITE.resolveModelAttribute(context, operation).asBoolean(true);
        List<ModelNode> contents = CONTENT_PARAM_ALL_EXPLODED.resolveModelAttribute(context, operation).asList();
        final List<ExplodedContent> addedFiles = new ArrayList<>(contents.size());
        final byte[] newHash;
        if (contents.size() == 1 && contents.get(0).hasDefined(HASH)) {
            newHash = DeploymentHandlerUtil.addFromHash(contentRepository, contents.get(0), managementName, address, context);
            if (operation.hasDefined(DeploymentAttributes.UPDATED_PATHS.getName())) {
                for (ModelNode addedFile : DeploymentAttributes.UPDATED_PATHS.resolveModelAttribute(context, operation).asList()) {
                    addedFiles.add(new ExplodedContent(addedFile.asString()));
                }
            }
        } else {
            for (ModelNode content : contents) {
                InputStream in;
                if(DeploymentHandlerUtils.hasValidContentAdditionParameterDefined(content)) {
                    in = DeploymentHandlerUtils.getInputStream(context, content);
                } else {
                    in = null;
                }
                String path = TARGET_PATH.resolveModelAttribute(context, content).asString();
                addedFiles.add(new ExplodedContent(path, in));
            }
            try {
                newHash = contentRepository.addContentToExploded(oldHash, addedFiles, overwrite);
            } catch (ExplodedContentException e) {
                throw createFailureException(e.toString());
            }
        }
        final List<String> relativePaths = new ArrayList<>();
        for (ExplodedContent addedFile : addedFiles) {
            String relativePath = addedFile.getRelativePath();
            relativePaths.add(relativePath);
        }
        contentItemNode.get(CONTENT_HASH.getName()).set(newHash);
        contentItemNode.get(CONTENT_ARCHIVE.getName()).set(false);
        if (!addedFiles.isEmpty() && ENABLED.resolveModelAttribute(context, deploymentResource.getModel()).asBoolean()) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    try {
                        ExecutorService executor = (ExecutorService) context.getServiceRegistry(false).getRequiredService(JBOSS_SERVER_EXECUTOR).getValue();
                        CountDownLatch latch = copy(executor, relativePaths, managementName, newHash);
                        if (latch != null) {
                            try {
                                if (!latch.await(60, TimeUnit.SECONDS)) {
                                    return;
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw createFailureException(e.toString());
                            }
                        }
                    } catch (IOException e) {
                        throw createFailureException(e.toString());
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

    private CountDownLatch copy(ExecutorService executor, List<String> relativePaths, String managementName, byte[] newHash) throws IOException {
        final CountDownLatch result;
        Path runtimeDeployedPath = DeploymentHandlerUtil.getExplodedDeploymentRoot(serverEnvironment, managementName);
        if (Files.exists(runtimeDeployedPath)) {
            result = new CountDownLatch(1);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        contentRepository.copyExplodedContentFiles(newHash, relativePaths, runtimeDeployedPath);
                    } catch (ExplodedContentException ex) {
                        ServerLogger.DEPLOYMENT_LOGGER.couldNotCopyFiles(ex, managementName);
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
