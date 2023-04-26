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

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.instmgr.logging.InstMgrLogger;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler that uploads a custom patch and stores it inside the server. It also subscribes the current installation
 * to a channel that consumes the maven repository of the custom patch.
 *
 */
public class InstMgrCustomPatchHandler extends InstMgrOperationStepHandler {

    public static final String OPERATION_NAME = "upload-custom-patch";

    private static final AttributeDefinition MANIFEST_GA = new SimpleAttributeDefinitionBuilder(InstMgrConstants.MANIFEST, ModelType.STRING)
            .setStorageRuntime()
            .setRequired(true)
            .setValidator(new ManifestValidator())
            .build();

    protected static final AttributeDefinition CUSTOM_PATCH_FILE = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.CUSTOM_PATCH_FILE, ModelType.INT)
            .setStorageRuntime()
            .setRequired(true)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.getResourceDescriptionResolver("custom-patch"))
            .addParameter(CUSTOM_PATCH_FILE)
            .addParameter(MANIFEST_GA)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    public InstMgrCustomPatchHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String manifestGA = MANIFEST_GA.resolveModelAttribute(context, operation).asString().replace(":", "_");
        final int customPathIndex = CUSTOM_PATCH_FILE.resolveModelAttribute(context, operation).asInt();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.acquireControllerLock();
                try {
                    final Path serverHome = imService.getHomeDir();
                    final Path baseTargetDir = imService.getCustomPatchDir(manifestGA);

                    final MavenOptions mavenOptions = new MavenOptions(null, false);
                    final InstallationManager im = imf.create(serverHome, mavenOptions);

                    // iterate over all the custom channels and check if there is a custom channel for the same manifest GAV
                    Collection<Channel> channels = im.listChannels();
                    Channel foundChannel = null;
                    for (Channel channel : channels) {
                        if(channel.getName().startsWith(InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX) && channel.getManifestCoordinate().get().equals(manifestGA)) {
                            foundChannel = channel;
                        }
                    }

                    // if so, delete any previous content an proceed
                    if (foundChannel != null) {
                        deleteDirIfExits(baseTargetDir, true);
                    }

                    baseTargetDir.toFile().mkdirs();

                    // save and unzip the file in the target dir for custom patches
                    try (InputStream is = context.getAttachmentStream(customPathIndex)) {
                        unzip(is, baseTargetDir);
                    }
                    final Path customPatchPath = getUploadedMvnRepoRoot(baseTargetDir);

                    // Build the channel
                    Repository customPatchRepository = new Repository("custom-patch", customPatchPath.toUri().toURL().toString());

                    final Channel customChannel =  new Channel(InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX + manifestGA, List.of(customPatchRepository), manifestGA);

                    if (foundChannel != null) {
                        im.changeChannel(customChannel);
                    } else {
                        im.addChannel(customChannel);
                    }
                    context.getResult().set(customPatchPath.toString());
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

    private static class ManifestValidator implements ParameterValidator {

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            final String manifestGA = value.asStringOrNull();

            if (manifestGA != null) {
                if (manifestGA.contains("\\") || manifestGA.contains("/")) {
                    throw InstMgrLogger.ROOT_LOGGER.invalidManifestGAV(manifestGA);
                }
                String[] parts = manifestGA.split(":");
                for (String part : parts) {
                    if (part == null || "".equals(part)) {
                        throw InstMgrLogger.ROOT_LOGGER.invalidManifestGAV(manifestGA);
                    }
                }
                if (parts.length != 2) {
                    throw InstMgrLogger.ROOT_LOGGER.invalidManifestGAOnly(manifestGA);
                }
            }
        }
    }
}
