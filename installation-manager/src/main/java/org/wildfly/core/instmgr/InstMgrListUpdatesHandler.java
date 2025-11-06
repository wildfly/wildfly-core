/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.InstallationUpdates;
import org.wildfly.installationmanager.ManifestVersion;
import org.wildfly.installationmanager.ManifestVersionChange;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation Handler that shows the available artifact updates.
 *
 */
public class InstMgrListUpdatesHandler extends AbstractInstMgrUpdateHandler {
    static final String OPERATION_NAME = "list-updates";
    protected static final AttributeDefinition MAVEN_REPO_FILE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.MAVEN_REPO_FILE, ModelType.INT)
            .setStorageRuntime()
            .setRequired(false)
            .build();

    protected static final AttributeDefinition MAVEN_REPO_FILES = new SimpleListAttributeDefinition.Builder(InstMgrConstants.MAVEN_REPO_FILES, MAVEN_REPO_FILE)
            .setStorageRuntime()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.REPOSITORIES)
            .build();

    protected static final AttributeDefinition REPOSITORIES = new ObjectListAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
            .setStorageRuntime()
            .setRequired(false)
            .setAlternatives(InstMgrConstants.MAVEN_REPO_FILES)
            .build();

    protected static final AttributeDefinition MANIFEST_VERSIONS = new ObjectListAttributeDefinition.Builder(InstMgrConstants.MANIFEST_VERSIONS, MANIFEST_VERSION_OBJ)
            .setStorageRuntime()
            .setRequired(false)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(OFFLINE)
            .addParameter(REPOSITORIES)
            .addParameter(MANIFEST_VERSIONS)
            .addParameter(LOCAL_CACHE)
            .addParameter(NO_RESOLVE_LOCAL_CACHE)
            .addParameter(USE_DEFAULT_LOCAL_CACHE)
            .addParameter(MAVEN_REPO_FILES)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    public InstMgrListUpdatesHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final boolean offline = OFFLINE.resolveModelAttribute(context, operation).asBoolean(false);
        final String pathLocalRepo = LOCAL_CACHE.resolveModelAttribute(context, operation).asStringOrNull();
        final Boolean noResolveLocalCache = NO_RESOLVE_LOCAL_CACHE.resolveModelAttribute(context, operation).asBooleanOrNull();
        final Boolean useDefaultLocalCache = USE_DEFAULT_LOCAL_CACHE.resolveModelAttribute(context, operation).asBooleanOrNull();
        final Path localRepository = pathLocalRepo != null ? Path.of(pathLocalRepo) : null;
        final List<ModelNode> mavenRepoFileIndexes = MAVEN_REPO_FILES.resolveModelAttribute(context, operation).asListOrEmpty();
        final List<ModelNode> repositoriesMn = REPOSITORIES.resolveModelAttribute(context, operation).asListOrEmpty();
        final List<ModelNode> manifestVersionsMn = MANIFEST_VERSIONS.resolveModelAttribute(context, operation).asListOrEmpty();

        if (noResolveLocalCache != null && useDefaultLocalCache !=null) {
            throw InstMgrLogger.ROOT_LOGGER.noResolveLocalCacheWithUseDefaultLocalCache();
        }

        if (pathLocalRepo != null && useDefaultLocalCache != null && useDefaultLocalCache) {
            throw InstMgrLogger.ROOT_LOGGER.localCacheWithUseDefaultLocalCache();
        }

        if (pathLocalRepo != null && noResolveLocalCache !=null && noResolveLocalCache) {
            throw InstMgrLogger.ROOT_LOGGER.localCacheWithNoResolveLocalCache();
        }

        if (!mavenRepoFileIndexes.isEmpty() && !repositoriesMn.isEmpty()) {
            throw InstMgrLogger.ROOT_LOGGER.mavenRepoFileWithRepositories();
        }

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.acquireControllerLock();
                try {
                    final Path homeDir = imService.getHomeDir();
                    boolean noResolveLocalCacheResult = noResolveLocalCache != null
                            ? noResolveLocalCache
                            : useDefaultLocalCache == null ? localRepository == null : (!useDefaultLocalCache && localRepository == null);
                    final MavenOptions mavenOptions = new MavenOptions(localRepository, noResolveLocalCacheResult, offline);
                    final InstallationManager im = imf.create(homeDir, mavenOptions);
                    final Path listUpdatesWorkDir = imService.createTempDir("list-updates-");

                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                                try {
                                    imService.deleteTempDir(listUpdatesWorkDir);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    });

                    final List<Repository> repositories = new ArrayList<>();
                    if (!mavenRepoFileIndexes.isEmpty()) {
                        InstMgrLogger.ROOT_LOGGER.debug("Adding possible custom patch repositories");
                        repositories.addAll(retrieveAllCustomPatchRepositories(im));
                        InstMgrLogger.ROOT_LOGGER.debug("Processing Streams from Operation Context");
                        repositories.addAll(getRepositoriesFromOperationStreams(context, mavenRepoFileIndexes, listUpdatesWorkDir));
                    } else {
                        repositories.addAll(toRepositories(context, repositoriesMn));
                    }

                    List<ManifestVersion> manifestVersions = toManifestVersions(context, manifestVersionsMn);

                    InstMgrLogger.ROOT_LOGGER.debug("Calling SPI to list updates with the following repositories:" + repositories);
                    final InstallationUpdates updates = im.findInstallationUpdates(repositories, manifestVersions);
                    final ModelNode resultValue = new ModelNode();
                    final ModelNode manifestUpdatesMn = new ModelNode().addEmptyList();
                    final ModelNode artifactUpdatesMn = new ModelNode().addEmptyList();

                    if (!updates.manifestUpdates().isEmpty()) {
                        for (ManifestVersionChange manifestChange : updates.manifestUpdates()) {
                            ModelNode manifestChangeMn = new ModelNode();
                            manifestChangeMn.get(InstMgrConstants.CHANNEL_NAME).set(manifestChange.getChannelName());
                            manifestChangeMn.get(InstMgrConstants.MANIFEST_LOCATION).set(manifestChange.getLocation());
                            manifestChangeMn.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).set(manifestChange.getCurrentVersion().getPhysicalVersion());
                            manifestChangeMn.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).set(manifestChange.getNewVersion().getPhysicalVersion());

                            String currentLogicalVersion = manifestChange.getCurrentVersion().getLogicalVersion();
                            if (currentLogicalVersion != null) {
                                manifestChangeMn.get(InstMgrConstants.LIST_UPDATES_OLD_LOGICAL_VERSION).set(currentLogicalVersion);
                            }

                            String newLogicalVersion = manifestChange.getNewVersion().getLogicalVersion();
                            if (newLogicalVersion != null) {
                                manifestChangeMn.get(InstMgrConstants.LIST_UPDATES_NEW_LOGICAL_VERSION).set(newLogicalVersion);
                            }

                            manifestChangeMn.get(InstMgrConstants.LIST_UPDATES_IS_DOWNGRADE).set(manifestChange.isDowngrade());
                            manifestUpdatesMn.add(manifestChangeMn);
                        }
                    }

                    if (!updates.artifactUpdates().isEmpty()) {
                        for (ArtifactChange artifactChange : updates.artifactUpdates()) {
                            ModelNode artifactChangeMn = new ModelNode();
                            artifactChangeMn.get(InstMgrConstants.LIST_UPDATES_STATUS).set(artifactChange.getStatus().name().toLowerCase(Locale.ENGLISH));
                            artifactChangeMn.get(InstMgrConstants.LIST_UPDATES_ARTIFACT_NAME).set(artifactChange.getArtifactName());
                            switch (artifactChange.getStatus()) {
                                case REMOVED:
                                    artifactChangeMn.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).set(artifactChange.getOldVersion());
                                    artifactChangeMn.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION);
                                    break;
                                case INSTALLED:
                                    artifactChangeMn.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION);
                                    artifactChangeMn.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).set(artifactChange.getNewVersion());
                                    break;
                                case UPDATED:
                                    artifactChangeMn.get(InstMgrConstants.LIST_UPDATES_OLD_VERSION).set(artifactChange.getOldVersion());
                                    artifactChangeMn.get(InstMgrConstants.LIST_UPDATES_NEW_VERSION).set(artifactChange.getNewVersion());
                                    break;
                                default:
                                    throw InstMgrLogger.ROOT_LOGGER.unexpectedArtifactChange(artifactChange.toString());
                            }
                            artifactUpdatesMn.add(artifactChangeMn);
                        }
                        if (!mavenRepoFileIndexes.isEmpty()) {
                            resultValue.get(InstMgrConstants.LIST_UPDATES_WORK_DIR).set(listUpdatesWorkDir.getFileName().toString());
                        } else {
                            imService.deleteTempDir(listUpdatesWorkDir);
                        }
                    } else {
                        imService.deleteTempDir(listUpdatesWorkDir);
                    }
                    resultValue.get(InstMgrConstants.LIST_UPDATES_RESULT).set(artifactUpdatesMn);
                    resultValue.get(InstMgrConstants.LIST_MANIFEST_UPDATES_RESULT).set(manifestUpdatesMn);

                    context.getResult().set(resultValue);

                } catch (ZipException e) {
                    throw new OperationFailedException(e.getLocalizedMessage());
                } catch (OperationFailedException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
