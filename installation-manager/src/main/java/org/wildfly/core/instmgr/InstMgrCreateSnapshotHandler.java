/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler that creates a snapshot of the installation-manager configuration.
 */
public class InstMgrCreateSnapshotHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "clone-export";
    private static final AttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PATH, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .setStorageRuntime()
            .setRequired(true)
            .build();

    protected static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RELATIVE_TO, ModelType.STRING, true)
            .setCapabilityReference("org.wildfly.management.path")
            .setStorageRuntime()
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(PATH)
            .addParameter(RELATIVE_TO)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    public InstMgrCreateSnapshotHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String path = PATH.resolveModelAttribute(context, operation).asString();
        final String relativeTo = RELATIVE_TO.resolveModelAttribute(context, operation).asStringOrNull();
        try {
            final Path exportPath = imService.resolvePath(path, relativeTo);
            validatePath(exportPath);
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    try {
                        Path serverHome = imService.getHomeDir();
                        MavenOptions mavenOptions = new MavenOptions(null, false);
                        InstallationManager installationManager = imf.create(serverHome, mavenOptions);
                        Path snapshot = installationManager.createSnapshot(exportPath);
                        context.getResult().set(snapshot.toString());
                    } catch (IllegalArgumentException e) {
                        throw new OperationFailedException(e);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        } catch (IllegalArgumentException e) {
            throw InstMgrLogger.ROOT_LOGGER.pathEntryNotFound(relativeTo);
        }
    }

    private void validatePath(Path exportPath) throws OperationFailedException {
        Path pathToVerify = exportPath;
        if (exportPath.toString().endsWith(".zip")) {
            pathToVerify = pathToVerify.getParent();
        }
        if (!Files.exists(pathToVerify)) {
            throw InstMgrLogger.ROOT_LOGGER.exportPathDoesNotExist(exportPath);
        }
        if (!Files.isWritable(pathToVerify)) {
            throw InstMgrLogger.ROOT_LOGGER.exportPathIsNotWritable(exportPath);
        }
    }
}
