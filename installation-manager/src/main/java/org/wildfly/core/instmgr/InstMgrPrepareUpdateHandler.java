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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

import java.io.File;
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
 * Operation handler that prepares a candidate server with the available updates.
 */
public class InstMgrPrepareUpdateHandler extends AbstractInstMgrUpdateHandler {
    static final String OPERATION_NAME = "prepare-updates";

    protected static final AttributeDefinition MAVEN_REPO_FILE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.MAVEN_REPO_FILE, ModelType.INT)
            .setStorageRuntime()
            .setRequired(false)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
            .build();

    protected static final AttributeDefinition MAVEN_REPO_FILES =  new SimpleListAttributeDefinition.Builder(InstMgrConstants.MAVEN_REPO_FILES, MAVEN_REPO_FILE)
            .setStorageRuntime()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.LIST_UPDATES_WORK_DIR, InstMgrConstants.REPOSITORIES)
            .build();

    protected static final AttributeDefinition LIST_UPDATES_WORK_DIR = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.LIST_UPDATES_WORK_DIR, ModelType.STRING)
            .setRequired(false)
            .setStorageRuntime()
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .setAlternatives(InstMgrConstants.MAVEN_REPO_FILES, InstMgrConstants.REPOSITORIES)
            .build();

    protected static final AttributeDefinition REPOSITORIES = new ObjectListAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.LIST_UPDATES_WORK_DIR, InstMgrConstants.MAVEN_REPO_FILES)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(OFFLINE)
            .addParameter(REPOSITORIES)
            .addParameter(LOCAL_CACHE)
            .addParameter(NO_RESOLVE_LOCAL_CACHE)
            .addParameter(MAVEN_REPO_FILES)
            .addParameter(LIST_UPDATES_WORK_DIR)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    InstMgrPrepareUpdateHandler(InstMgrService imService, InstallationManagerFactory imf) {
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
        final String listUpdatesWorkDir = LIST_UPDATES_WORK_DIR.resolveModelAttribute(context, operation).asStringOrNull();

        if (pathLocalRepo != null && noResolveLocalCache) {
            throw InstMgrLogger.ROOT_LOGGER.localCacheWithNoResolveLocalCache();
        }

        if (listUpdatesWorkDir != null && (!repositoriesMn.isEmpty() || !mavenRepoFileIndexes.isEmpty())) {
            throw InstMgrLogger.ROOT_LOGGER.workDirWithMavenRepoFileOrRepositories();
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
                    if (listUpdatesWorkDir != null) {
                        InstMgrLogger.ROOT_LOGGER.debug("Preparing a server candidate by using the workdir:" + listUpdatesWorkDir);
                        // We are coming from a previous list-updates management operation where a Maven Zip Repository
                        // has been uploaded and unzipped on a temp dir.
                        final Path mvnRepoWorkDir = imService.getTempDirByName(listUpdatesWorkDir);
                        addCompleteStep(context, imService, listUpdatesWorkDir);

                        for (File file : mvnRepoWorkDir.toFile().listFiles((dir, name) -> name.startsWith(InstMgrConstants.INTERNAL_REPO_PREFIX))) {
                            Path repoIdPath = mvnRepoWorkDir.resolve(file.getName());
                            Path uploadedRepoZipRootDir = getUploadedMvnRepoRoot(repoIdPath);
                            Repository uploadedMavenRepo = new Repository(file.getName(), uploadedRepoZipRootDir.toUri().toURL().toExternalForm());
                            repositories.add(uploadedMavenRepo);
                        }
                    } else if (!mavenRepoFileIndexes.isEmpty()) {
                        // We are uploading a Maven Zip Repository
                        InstMgrLogger.ROOT_LOGGER.debug("Preparing a server candidate by using Operation Streams");
                        final Path prepareUpdateWorkDir = imService.createTempDir("prepare-updates-");
                        addCompleteStep(context, imService, prepareUpdateWorkDir.getFileName().toString());
                        repositories.addAll(getRepositoriesFromOperationStreams(context, mavenRepoFileIndexes, prepareUpdateWorkDir));
                    } else {
                        repositories.addAll(toRepositories(context, repositoriesMn));
                    }

                    InstMgrLogger.ROOT_LOGGER.debugf("Calling SPI to prepare an updates at [%s] with the following repositories [%s]",
                            imService.getPreparedServerDir(), repositories);
                    Files.createDirectories(imService.getPreparedServerDir());
                    boolean prepared = im.prepareUpdate(imService.getPreparedServerDir(), repositories);
                    if (prepared) {
                        // Put server in restart required?
                        // No. The documented purpose of the restart-required state is to indicate that the process need to be
                        // restarted to bring its runtime state into alignment with its persistent configuration. This OSH does
                        // not affect the existing server's persistent configuration, so restart-required is not appropriate.
                        // Using it to inform users of other reasons why they would want to restart the server is beyond the
                        // intended purpose of restart-required.
                        //
                        // Also, once put in restart required, the clean operation should revert it if it cleans the prepared
                        // server, but we cannot revert the restart flag from a different Operation since there could be other
                        // Operations executed which could have been set this flag.
                        context.getResult().set(imService.getPreparedServerDir().normalize().toAbsolutePath().toString());

                        final String applyUpdate = im.generateApplyUpdateCommand(homeDir.resolve("bin"), imService.getPreparedServerDir());
                        InstMgrLogger.ROOT_LOGGER.debug("Apply Revert Command: " + applyUpdate);

                        imService.commitCandidateServer(applyUpdate);
                    } else {
                        imService.resetCandidateStatus();
                    }
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

    private void addCompleteStep(OperationContext context, InstMgrService imService, String workDir) {
        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                try {
                    imService.deleteTempDir(workDir);
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
