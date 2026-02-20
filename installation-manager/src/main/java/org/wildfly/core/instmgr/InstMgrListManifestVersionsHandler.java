/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

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
import org.wildfly.installationmanager.AvailableManifestVersions;
import org.wildfly.installationmanager.ManifestVersionPair;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;

/**
 * Operation Handler that shows the available artifact updates.
 *
 */
public class InstMgrListManifestVersionsHandler extends AbstractInstMgrUpdateHandler {
    public static final String OPERATION_NAME = "list-manifest-versions";
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

    protected static final AttributeDefinition INCLUDE_DOWNGRADES = new SimpleAttributeDefinitionBuilder(InstMgrConstants.INCLUDE_DOWNGRADES, ModelType.BOOLEAN)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(OFFLINE)
            .addParameter(REPOSITORIES)
            .addParameter(LOCAL_CACHE)
            .addParameter(USE_DEFAULT_LOCAL_CACHE)
            .addParameter(MAVEN_REPO_FILES)
            .addParameter(INCLUDE_DOWNGRADES)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    public InstMgrListManifestVersionsHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final boolean offline = OFFLINE.resolveModelAttribute(context, operation).asBoolean(false);
        final String pathLocalRepo = LOCAL_CACHE.resolveModelAttribute(context, operation).asStringOrNull();
        final Boolean useDefaultLocalCache = USE_DEFAULT_LOCAL_CACHE.resolveModelAttribute(context, operation).asBooleanOrNull();
        final Path localRepository = pathLocalRepo != null ? Path.of(pathLocalRepo) : null;
        final List<ModelNode> mavenRepoFileIndexes = MAVEN_REPO_FILES.resolveModelAttribute(context, operation).asListOrEmpty();
        final List<ModelNode> repositoriesMn = REPOSITORIES.resolveModelAttribute(context, operation).asListOrEmpty();
        final boolean includeDowngrades = INCLUDE_DOWNGRADES.resolveModelAttribute(context, operation).asBoolean();

        if (pathLocalRepo != null && useDefaultLocalCache != null && useDefaultLocalCache) {
            throw InstMgrLogger.ROOT_LOGGER.localCacheWithUseDefaultLocalCache();
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
                    boolean noResolveLocalCacheResult = useDefaultLocalCache == null ? localRepository == null : (!useDefaultLocalCache && localRepository == null);
                    final MavenOptions mavenOptions = new MavenOptions(localRepository, noResolveLocalCacheResult, offline);
                    final InstallationManager im = imf.create(homeDir, mavenOptions);
                    final Path workDir = imService.createTempDir("list-manifest-versions-");

                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            try {
                                imService.deleteTempDir(workDir);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });

                    final List<Repository> repositories = new ArrayList<>();
                    if (!mavenRepoFileIndexes.isEmpty()) {
                        InstMgrLogger.ROOT_LOGGER.debug("Adding possible custom patch repositories");
                        repositories.addAll(retrieveAllCustomPatchRepositories(im));
                        InstMgrLogger.ROOT_LOGGER.debug("Processing Streams from Operation Context");
                        repositories.addAll(getRepositoriesFromOperationStreams(context, mavenRepoFileIndexes, workDir));
                    } else {
                        repositories.addAll(toRepositories(context, repositoriesMn));
                    }


                    InstMgrLogger.ROOT_LOGGER.debug("Calling SPI to list updates with the following repositories:" + repositories);
                    final List<AvailableManifestVersions> manifestsVersions =
                            im.findAvailableManifestVersions(repositories, includeDowngrades);
                    final ModelNode resultList = new ModelNode().addEmptyList();

                    for (AvailableManifestVersions manifestVersion : manifestsVersions) {
                        final ModelNode manifestInfo = new ModelNode();

                        manifestInfo.get(InstMgrConstants.CHANNEL_NAME).set(manifestVersion.getChannelName());
                        manifestInfo.get(InstMgrConstants.MANIFEST_LOCATION).set(manifestVersion.getLocation());
                        manifestInfo.get(InstMgrConstants.MANIFEST_CURRENT_VERSION)
                                .set(manifestVersion.getCurrentVersion().getPhysicalVersion());
                        String currentLogicalVersion = manifestVersion.getCurrentVersion().getLogicalVersion();
                        if (currentLogicalVersion != null) {
                            manifestInfo.get(InstMgrConstants.MANIFEST_CURRENT_LOGICAL_VERSION)
                                    .set(currentLogicalVersion);
                        }

                        ModelNode versionListMn = manifestInfo.get(InstMgrConstants.MANIFEST_VERSIONS);
                        for (ManifestVersionPair version: manifestVersion.getAvailableVersions()) {
                            ModelNode versionMn = new ModelNode();

                            versionMn.get(InstMgrConstants.MANIFEST_VERSION).set(version.getPhysicalVersion());
                            if (version.getLogicalVersion() != null) {
                                versionMn.get(InstMgrConstants.MANIFEST_LOGICAL_VERSION).set(version.getLogicalVersion());
                            }
                            versionListMn.add(versionMn);
                        }

                        resultList.add(manifestInfo);
                    }

                    context.getResult().set(resultList.asList());

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
