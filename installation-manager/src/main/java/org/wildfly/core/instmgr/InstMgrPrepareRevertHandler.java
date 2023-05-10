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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler that prepares a candidate server reverted to a previous installation state.
 *
 */
public class InstMgrPrepareRevertHandler extends AbstractInstMgrUpdateHandler {

    public static final String OPERATION_NAME = "prepare-revert";

    private static final AttributeDefinition REVISION = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.REVISION, ModelType.STRING)
            .setRequired(true)
            .setStorageRuntime()
            .build();

    static final AttributeDefinition MAVEN_REPO_FILE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.MAVEN_REPO_FILE, ModelType.INT)
            .setStorageRuntime()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.REPOSITORIES)
            .build();

    protected static final AttributeDefinition MAVEN_REPO_FILES =  new SimpleListAttributeDefinition.Builder(InstMgrConstants.MAVEN_REPO_FILES, MAVEN_REPO_FILE)
            .setStorageRuntime()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.REPOSITORIES)
            .build();

    static final AttributeDefinition REPOSITORIES = new ObjectListAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
            .setStorageRuntime()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.MAVEN_REPO_FILE)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(REVISION)
            .addParameter(OFFLINE)
            .addParameter(REPOSITORIES)
            .addParameter(LOCAL_CACHE)
            .addParameter(NO_RESOLVE_LOCAL_CACHE)
            .addParameter(MAVEN_REPO_FILES)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    InstMgrPrepareRevertHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final boolean offline = OFFLINE.resolveModelAttribute(context, operation).asBoolean(false);
        final String pathLocalRepo = LOCAL_CACHE.resolveModelAttribute(context, operation).asStringOrNull();
        final boolean noResolveLocalCache = NO_RESOLVE_LOCAL_CACHE.resolveModelAttribute(context, operation).asBoolean(false);
        final Path localRepository = pathLocalRepo != null ? Path.of(pathLocalRepo) : null;
        final List<ModelNode> mavenRepoFileIndexes = MAVEN_REPO_FILES.resolveModelAttribute(context, operation).asListOrEmpty();
        final List<ModelNode> repositoriesMn = REPOSITORIES.resolveModelAttribute(context, operation).asListOrEmpty();
        final String revision = REVISION.resolveModelAttribute(context, operation).asString();

        if (pathLocalRepo != null && noResolveLocalCache) {
            throw InstMgrLogger.ROOT_LOGGER.localCacheWithNoResolveLocalCache();
        }

        if (!mavenRepoFileIndexes.isEmpty() && !repositoriesMn.isEmpty()) {
            throw InstMgrLogger.ROOT_LOGGER.mavenRepoFileWithRepositories();
        }

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.acquireControllerLock();

                if (!imService.canPrepareServer()) {
                    throw InstMgrLogger.ROOT_LOGGER.serverAlreadyPrepared();
                }
                imService.beginCandidateServer();
                addCompleteStep(context, imService, null);

                try {
                    final Path homeDir = imService.getHomeDir();
                    final MavenOptions mavenOptions = new MavenOptions(localRepository, noResolveLocalCache, offline);
                    final InstallationManager im = imf.create(homeDir, mavenOptions);

                    final List<Repository> repositories = new ArrayList<>();
                    if (!mavenRepoFileIndexes.isEmpty()) {
                        InstMgrLogger.ROOT_LOGGER.debug("Preparing a server candidate to revert by using Operation Streams");
                        final Path preparationWorkDir = imService.createTempDir("prepare-revert-");
                        addCompleteStep(context, imService, preparationWorkDir.getFileName().toString());
                        repositories.addAll(getRepositoriesFromOperationStreams(context, mavenRepoFileIndexes, preparationWorkDir));
                    } else {
                        repositories.addAll(toRepositories(context, repositoriesMn));
                    }

                    InstMgrLogger.ROOT_LOGGER.debugf("Calling SPI to prepare an revert at [%s] with the following repositories [%s] and revision [%s]",
                            imService.getPreparedServerDir(), repositories, revision);
                    Files.createDirectories(imService.getPreparedServerDir());
                    im.prepareRevert(revision, imService.getPreparedServerDir(), repositories);
                    final String applyRevert = im.generateApplyRevertCommand(homeDir.resolve("bin"), imService.getPreparedServerDir());
                    InstMgrLogger.ROOT_LOGGER.debug("Apply Revert Command: " + applyRevert);
                    imService.commitCandidateServer(applyRevert);

                    context.getResult().set(imService.getPreparedServerDir().normalize().toAbsolutePath().toString());
                } catch (ZipException e) {
                    throw InstMgrLogger.ROOT_LOGGER.invalidMavenRepoFile(e.getLocalizedMessage());
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private void addCompleteStep(OperationContext context, InstMgrService imService, String mavenRepoParentWorkdir) {
        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                try {
                    imService.deleteTempDir(mavenRepoParentWorkdir);
                    if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                        imService.resetCandidateStatus();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
