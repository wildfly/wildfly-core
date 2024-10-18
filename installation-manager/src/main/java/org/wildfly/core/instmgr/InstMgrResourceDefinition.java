/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.ObjectTypeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.TrustCertificate;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Installation manager resource definition.
 */
class InstMgrResourceDefinition extends SimpleResourceDefinition {

    static final String PATH_MANAGER_CAP = "org.wildfly.management.path-manager";
    static final RuntimeCapability<Void> INSTALLATION_MANAGER_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.core.installationmanager", InstMgrResourceDefinition.class)
                    .addRequirements(PATH_MANAGER_CAP)
                    .build();
    private final InstMgrService imService;
    private final InstallationManagerFactory imf;

    private static final AttributeDefinition REPOSITORY_ID = new SimpleAttributeDefinitionBuilder(InstMgrConstants.REPOSITORY_ID, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition REPOSITORY_URL = new SimpleAttributeDefinitionBuilder(InstMgrConstants.REPOSITORY_URL, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final ObjectTypeAttributeDefinition REPOSITORY = new ObjectTypeAttributeDefinition.Builder(InstMgrConstants.REPOSITORY, REPOSITORY_ID, REPOSITORY_URL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .setSuffix("repository")
            .build();

    private static final AttributeDefinition REPOSITORIES = new ObjectListAttributeDefinition.Builder(InstMgrConstants.REPOSITORIES, REPOSITORY)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition CHANNEL_NAME = new SimpleAttributeDefinitionBuilder(InstMgrConstants.CHANNEL_NAME, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition KEY_ID = new SimpleAttributeDefinitionBuilder(InstMgrConstants.CERT_KEY_ID, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition MANIFEST_GAV = new SimpleAttributeDefinitionBuilder(InstMgrConstants.MANIFEST_GAV, ModelType.STRING)
            .setAlternatives(InstMgrConstants.MANIFEST_URL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition MANIFEST_URL = new SimpleAttributeDefinitionBuilder(InstMgrConstants.MANIFEST_URL, ModelType.STRING)
            .setAlternatives(InstMgrConstants.MANIFEST_GAV)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static final AttributeDefinition MANIFEST = ObjectTypeAttributeDefinition.create(InstMgrConstants.MANIFEST, MANIFEST_GAV, MANIFEST_URL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .setSuffix("manifest")
            .build();

    private static final ObjectTypeAttributeDefinition CHANNEL = ObjectTypeAttributeDefinition.create(InstMgrConstants.CHANNEL, CHANNEL_NAME, REPOSITORIES, MANIFEST)
            .setValidator(new ChannelValidator())
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .setSuffix("channel")
            .build();

    private static final ObjectTypeAttributeDefinition CERTIFICATE = ObjectTypeAttributeDefinition.create("certificate", KEY_ID)
//            .setValidator(new ChannelValidator())
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .setSuffix("certificate")
            .build();

    private static final AttributeDefinition CHANNELS = ObjectListAttributeDefinition.Builder.of(InstMgrConstants.CHANNELS, CHANNEL)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static final AttributeDefinition CERTIFICATES = ObjectListAttributeDefinition.Builder.of("certificates", CERTIFICATE)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    public static PathElement getPath(String name) {
        return PathElement.pathElement(CORE_SERVICE, name);
    }

    public InstMgrResourceDefinition(InstallationManagerFactory imf, InstMgrService imService) {
        super(new Parameters(getPath(InstMgrConstants.TOOL_NAME), InstMgrResolver.RESOLVER)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.PATCHING)
                .setCapabilities(INSTALLATION_MANAGER_CAPABILITY)
                .setRuntime()
        );
        this.imf = imf;
        this.imService = imService;
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        InstMgrHistoryRevisionHandler historyRevisionHandler = new InstMgrHistoryRevisionHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrHistoryRevisionHandler.DEFINITION, historyRevisionHandler);

        InstMgrHistoryHandler historyHandler = new InstMgrHistoryHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrHistoryHandler.DEFINITION, historyHandler);

        InstMgrCreateSnapshotHandler createSnapshotHandler = new InstMgrCreateSnapshotHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrCreateSnapshotHandler.DEFINITION, createSnapshotHandler);

        InstMgrListUpdatesHandler lstUpdatesHandler = new InstMgrListUpdatesHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrListUpdatesHandler.DEFINITION, lstUpdatesHandler);

        InstMgrCleanHandler clean = new InstMgrCleanHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrCleanHandler.DEFINITION, clean);

        InstMgrPrepareUpdateHandler prepUpdatesHandler = new InstMgrPrepareUpdateHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrPrepareUpdateHandler.DEFINITION, prepUpdatesHandler);

        InstMgrPrepareRevertHandler revertHandler = new InstMgrPrepareRevertHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrPrepareRevertHandler.DEFINITION, revertHandler);

        InstMgrRemoveChannelHandler channelRemoveHandler = new InstMgrRemoveChannelHandler(imService, imf);
        resourceRegistration.registerOperationHandler(channelRemoveHandler.DEFINITION, channelRemoveHandler);

        InstMgrCustomPatchUploadHandler customPatchUploadHandler = new InstMgrCustomPatchUploadHandler(imService, imf);
        resourceRegistration.registerOperationHandler(customPatchUploadHandler.DEFINITION, customPatchUploadHandler);

        InstMgrCustomPatchRemoveHandler customPatchRemoveHandler = new InstMgrCustomPatchRemoveHandler(imService, imf);
        resourceRegistration.registerOperationHandler(customPatchRemoveHandler.DEFINITION, customPatchRemoveHandler);

        InstMgrCertificateParseHandler certificateParseHandler = new InstMgrCertificateParseHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrCertificateParseHandler.DEFINITION, certificateParseHandler);

        InstMgrCertificateImportHandler certificateImportHandler = new InstMgrCertificateImportHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrCertificateImportHandler.DEFINITION, certificateImportHandler);

        InstMgrCertificateRemoveHandler certificateRemoveHandler = new InstMgrCertificateRemoveHandler(imService, imf);
        resourceRegistration.registerOperationHandler(InstMgrCertificateRemoveHandler.DEFINITION, certificateRemoveHandler);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(CHANNELS, new ReadHandler(), new WriteHandler());
        resourceRegistration.registerReadOnlyAttribute(CERTIFICATES, new CertReadHandler());
    }

    private class WriteHandler implements OperationStepHandler {

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final List<ModelNode> channelsListMn = new ArrayList<>();
            if (operation.hasDefined(VALUE)) {
                channelsListMn.addAll(CHANNELS.resolveValue(context, operation.get(VALUE)).asList());
            }
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    context.acquireControllerLock();
                    try {
                        final Path serverHome = imService.getHomeDir();

                        final MavenOptions mavenOptions = new MavenOptions(null, false);
                        final InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                        final Collection<Channel> exitingChannels = installationManager.listChannels();
                        final Set<String> exitingChannelNames = new HashSet<>();
                        for (Channel c : exitingChannels) {
                            exitingChannelNames.add(c.getName());
                        }

                        if (channelsListMn.isEmpty()) {
                            for (Channel c : exitingChannels) {
                                installationManager.removeChannel(c.getName());
                            }
                        } else {
                            for (ModelNode mChannel : channelsListMn) {
                                final String cName = mChannel.get(InstMgrConstants.CHANNEL_NAME).asString();
                                final List<Repository> repositories = new ArrayList<>();

                                // channel.repositories
                                final List<ModelNode> mRepositories = mChannel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
                                for (ModelNode mRepository : mRepositories) {
                                    String id = mRepository.get(InstMgrConstants.REPOSITORY_ID).asString();
                                    String url = mRepository.get(InstMgrConstants.REPOSITORY_URL).asString();
                                    Repository repository = new Repository(id, new URI(url).toURL().toExternalForm());
                                    repositories.add(repository);
                                }

                                // channel.manifest
                                String manifestGav;
                                URL manifestUrl;
                                Channel c;
                                if (mChannel.hasDefined(InstMgrConstants.MANIFEST)) {
                                    ModelNode mManifest = mChannel.get(InstMgrConstants.MANIFEST);
                                    if (mManifest.hasDefined(InstMgrConstants.MANIFEST_GAV)) {
                                        manifestGav = mManifest.get(InstMgrConstants.MANIFEST_GAV).asString();
                                        c = new Channel(cName, repositories, manifestGav);
                                    } else {
                                        manifestUrl = new URI(mManifest.get(InstMgrConstants.REPOSITORY_URL).asString()).toURL();
                                        c = new Channel(cName, repositories, manifestUrl);
                                    }
                                } else {
                                    c = new Channel(cName, repositories);
                                }

                                if (exitingChannelNames.contains(c.getName())) {
                                    installationManager.changeChannel(c);
                                } else {
                                    installationManager.addChannel(c);
                                }
                            }
                        }
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    private class ReadHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    try {
                        final ModelNode result = context.getResult();
                        Path serverHome = imService.getHomeDir();

                        MavenOptions mavenOptions = new MavenOptions(null, false);
                        InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                        ModelNode mChannels = new ModelNode().addEmptyList();
                        Collection<Channel> channels = installationManager.listChannels();
                        for (Channel channel : channels) {
                            ModelNode mChannel = new ModelNode();
                            mChannel.get(InstMgrConstants.CHANNEL_NAME).set(channel.getName());

                            ModelNode mRepositories = new ModelNode().addEmptyList();
                            for (Repository repository : channel.getRepositories()) {
                                ModelNode mRepository = new ModelNode();
                                mRepository.get(InstMgrConstants.REPOSITORY_ID).set(repository.getId());
                                mRepository.get(InstMgrConstants.REPOSITORY_URL).set(repository.getUrl());
                                mRepositories.add(mRepository);
                            }
                            mChannel.get(InstMgrConstants.REPOSITORIES).set(mRepositories);

                            ModelNode mManifest = new ModelNode();
                            if (channel.getManifestCoordinate().isPresent()) {
                                mManifest.get(InstMgrConstants.MANIFEST_GAV).set(channel.getManifestCoordinate().get());
                                mChannel.get(InstMgrConstants.MANIFEST).set(mManifest);
                            } else if (channel.getManifestUrl().isPresent()) {
                                mManifest.get(InstMgrConstants.MANIFEST_URL)
                                        .set(channel.getManifestUrl().get().toExternalForm());
                                mChannel.get(InstMgrConstants.MANIFEST).set(mManifest);
                            }
                            mChannels.add(mChannel);
                        }
                        result.set(mChannels);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    private static class ChannelValidator extends ObjectTypeValidator {
        public ChannelValidator() {
            super(true, CHANNEL_NAME, REPOSITORIES, MANIFEST);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);

            if (!value.hasDefined(NAME)) {
                throw InstMgrLogger.ROOT_LOGGER.missingChannelName();
            }
            String channelName = value.get(NAME).asString();
            // validate repositories
            if (!value.hasDefined(InstMgrConstants.REPOSITORIES)) {
                throw InstMgrLogger.ROOT_LOGGER.noChannelRepositoriesDefined(channelName);
            }

            List<ModelNode> repositoriesMn = value.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
            for (ModelNode repository : repositoriesMn) {
                String repoUrl = repository.get(InstMgrConstants.REPOSITORY_URL).asStringOrNull();
                if (repoUrl == null) {
                    throw InstMgrLogger.ROOT_LOGGER.noChannelRepositoryURLDefined(channelName);
                }
                try {
                    new URI(repoUrl).toURL();
                } catch (MalformedURLException | URISyntaxException e) {
                    throw InstMgrLogger.ROOT_LOGGER.invalidChannelRepositoryURL(repoUrl, channelName);
                }
                String repoId = repository.get(InstMgrConstants.REPOSITORY_ID).asStringOrNull();
                if (repoId == null) {
                    throw InstMgrLogger.ROOT_LOGGER.noChannelRepositoryIDDefined(channelName);
                }
            }

            // validate manifest
            if (value.hasDefined(InstMgrConstants.MANIFEST)) {
                String gav = value.get(InstMgrConstants.MANIFEST).get(InstMgrConstants.MANIFEST_GAV).asStringOrNull();
                String url = value.get(InstMgrConstants.MANIFEST).get(InstMgrConstants.MANIFEST_URL).asStringOrNull();

                if (gav != null) {
                    if (gav.contains("\\") || gav.contains("/")) {
                        throw InstMgrLogger.ROOT_LOGGER.invalidChannelManifestGAV(gav, channelName);
                    }
                    String[] parts = gav.split(":");
                    for (String part : parts) {
                        if (part == null || "".equals(part)) {
                            throw InstMgrLogger.ROOT_LOGGER.invalidChannelManifestGAV(gav, channelName);
                        }
                    }
                    if (parts.length != 2 && parts.length != 3) { // GA or GAV
                        throw InstMgrLogger.ROOT_LOGGER.invalidChannelManifestGAV(gav, channelName);
                    }
                }
                if (url != null) {
                    try {
                        new URI(url).toURL();
                    } catch (MalformedURLException | URISyntaxException e) {
                        throw InstMgrLogger.ROOT_LOGGER.invalidChannelManifestURL(url, channelName);
                    }
                }
            }
        }
    }

    private class CertReadHandler implements OperationStepHandler {
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    try {
                        final ModelNode result = context.getResult();
                        Path serverHome = imService.getHomeDir();

                        MavenOptions mavenOptions = new MavenOptions(null, false);
                        InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                        ModelNode mCertificates = new ModelNode().addEmptyList();
                        Collection<TrustCertificate> trustedCertificates = installationManager.listCA();
                        for (TrustCertificate tc : trustedCertificates) {
                            ModelNode entry = new ModelNode();
                            entry.get(InstMgrConstants.CERT_KEY_ID).set(tc.getKeyID());
                            entry.get(InstMgrConstants.CERT_FINGERPRINT).set(tc.getFingerprint());
                            entry.get(InstMgrConstants.CERT_DESCRIPTION).set(tc.getDescription());
                            entry.get(InstMgrConstants.CERT_STATUS).set(tc.getStatus());
                            mCertificates.add(entry);
                        }
                        result.set(mCertificates);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }
}
