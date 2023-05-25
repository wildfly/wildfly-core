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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
import org.wildfly.installationmanager.InstallationChanges;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler to get the history of the installation manager changes, either artifacts or configuration metadata as
 * channel changes from a specific revision.
 */
public class InstMgrHistoryRevisionHandler extends InstMgrOperationStepHandler {
    public static final String OPERATION_NAME = "history-from-revision";

    private static final AttributeDefinition REVISION = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.REVISION, ModelType.STRING).setStorageRuntime()
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER).addParameter(REVISION)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY).setReplyType(ModelType.LIST).setRuntimeOnly().setReplyValueType(ModelType.OBJECT).build();

    InstMgrHistoryRevisionHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String revision = REVISION.resolveModelAttribute(context, operation).asString();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    Path serverHome = imService.getHomeDir();
                    MavenOptions mavenOptions = new MavenOptions(null, false);
                    InstallationManager installationManager = imf.create(serverHome, mavenOptions);
                    ModelNode resulList = new ModelNode();

                    InstallationChanges changes = installationManager.revisionDetails(revision);
                    List<ArtifactChange> artifactChanges = changes.artifactChanges();
                    List<ChannelChange> channelChanges = changes.channelChanges();
                    if (!artifactChanges.isEmpty()) {
                        for (ArtifactChange artifactChange : artifactChanges) {
                            ModelNode artifactChangeMn = new ModelNode();
                            artifactChangeMn.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).set(artifactChange.getArtifactName());
                            artifactChangeMn.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_STATUS).set(artifactChange.getStatus().name().toLowerCase());
                            switch (artifactChange.getStatus()) {
                                case REMOVED:
                                    artifactChangeMn.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).set(artifactChange.getOldVersion());
                                    artifactChangeMn.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION);
                                    break;
                                case INSTALLED:
                                    artifactChangeMn.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION);
                                    artifactChangeMn.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).set(artifactChange.getNewVersion());
                                    break;
                                case UPDATED:
                                    artifactChangeMn.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).set(artifactChange.getOldVersion());
                                    artifactChangeMn.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).set(artifactChange.getNewVersion());
                                    break;
                                default:
                                    throw InstMgrLogger.ROOT_LOGGER.unexpectedArtifactChange(artifactChange.toString());
                            }
                            resulList.get(InstMgrConstants.HISTORY_RESULT_DETAILED_ARTIFACT_CHANGES).add(artifactChangeMn);
                        }
                    }

                    if (!channelChanges.isEmpty()) {
                        for (ChannelChange channelChange : channelChanges) {
                            ModelNode channelChangeMn = new ModelNode();
                            channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_STATUS).set(channelChange.getStatus().name().toLowerCase());
                            switch (channelChange.getStatus()) {
                                case REMOVED: {
                                    Channel channel = channelChange.getOldChannel().get();
                                    channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).set(String.format(channel.getName()));

                                    String manifest = getManifest(channel);
                                    if (!"".equals(manifest)) {
                                        ModelNode manifestMn = channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_MANIFEST);
                                        manifestMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).set(manifest);
                                        manifestMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST);
                                    }

                                    ModelNode repositoriesMn = channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES);
                                    List<Repository> repositories = channel.getRepositories();
                                    for (Repository repository : repositories) {
                                        ModelNode repositoryMn = new ModelNode();
                                        repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).set(repository.asFormattedString());
                                        repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY);
                                        repositoriesMn.add(repositoryMn);
                                    }
                                    break;
                                }
                                case ADDED: {
                                    Channel channel = channelChange.getNewChannel().get();
                                    channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).set(String.format(channel.getName()));

                                    String manifest = getManifest(channel);
                                    if (!"".equals(manifest)) {
                                        ModelNode manifestMn = channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_MANIFEST);
                                        manifestMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST);
                                        manifestMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).set(manifest);
                                    }

                                    ModelNode repositoriesMn = channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES);
                                    List<Repository> repositories = channel.getRepositories();
                                    for (Repository repository : repositories) {
                                        ModelNode repositoryMn = new ModelNode();
                                        repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY);
                                        repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).set(repository.asFormattedString());
                                        repositoriesMn.add(repositoryMn);
                                    }
                                    break;
                                }
                                case MODIFIED: {
                                    Channel channel = channelChange.getNewChannel().get();
                                    channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).set(String.format(channel.getName()));
                                    Channel oldChannel = channelChange.getOldChannel().get();
                                    Channel newChannel = channelChange.getNewChannel().get();

                                    String oldManifest = getManifest(oldChannel);
                                    String newManifest = getManifest(newChannel);

                                    if (!"".equals(oldManifest) || !"".equals(newManifest)) {
                                        ModelNode manifestMn = channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_MANIFEST);

                                        ModelNode oldManifestMn = "".equals(oldManifest) ? new ModelNode() : new ModelNode(oldManifest);
                                        ModelNode newManifestMn = "".equals(newManifest) ? new ModelNode() : new ModelNode(newManifest);
                                        manifestMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).set(oldManifestMn);
                                        manifestMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).set(newManifestMn);
                                    }

                                    ModelNode repositoriesMn = channelChangeMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES);
                                    List<Repository> oldRepositoriesLst = new ArrayList<>(oldChannel.getRepositories());
                                    List<Repository> newRepositoriesLst = new ArrayList<>(newChannel.getRepositories());
                                    Iterator<Repository> newIt = newRepositoriesLst.iterator();
                                    Iterator<Repository> oldIt = oldRepositoriesLst.iterator();

                                    for (; oldIt.hasNext();) {
                                        ModelNode repositoryMn = new ModelNode();
                                        Repository oldRepository = oldIt.next();
                                        repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).set(oldRepository.asFormattedString());
                                        oldIt.remove();
                                        if (newIt.hasNext()) {
                                            Repository newRepository = newIt.next();
                                            repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).set(newRepository.asFormattedString());
                                            newIt.remove();
                                        } else {
                                            repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY);
                                        }
                                        repositoriesMn.add(repositoryMn);
                                    }

                                    for (; newIt.hasNext();) {
                                        ModelNode repositoryMn = new ModelNode();
                                        Repository newRepository = newIt.next();
                                        repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).set(newRepository.asFormattedString());
                                        newIt.remove();
                                        if (oldIt.hasNext()) {
                                            Repository oldRepository = oldIt.next();
                                            repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).set(oldRepository.asFormattedString());
                                            oldIt.remove();
                                        } else {
                                            repositoryMn.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY);
                                        }
                                        repositoriesMn.add(repositoryMn);
                                    }

                                    break;
                                }
                                default: {
                                    throw InstMgrLogger.ROOT_LOGGER.unexpectedConfigurationChange(channelChange.toString());
                                }
                            }
                            resulList.get(InstMgrConstants.HISTORY_RESULT_DETAILED_CHANNEL_CHANGES).add(channelChangeMn);
                        }
                    }

                    context.getResult().set(resulList);
                } catch (OperationFailedException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private String getManifest(Channel channel) {
        String manifest = "";
        if (channel.getManifestUrl().isPresent()) {
            manifest = channel.getManifestUrl().get().toString();
        } else if (channel.getManifestCoordinate().isPresent()) {
            manifest = channel.getManifestCoordinate().get();
        }
        return manifest;
    }
}
