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
import java.util.Collection;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 *  Operation handler that removes a custom patch and from the server. It also unsubscribes the installation from the channel that provides
 *  this custom patch and deletes this channel.
 */
public class InstMgrCustomPatchRemoveHandler extends InstMgrCustomPatchHandler {
    public static final String OPERATION_NAME = "remove-custom-patch";

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.getResourceDescriptionResolver("custom-patch"))
            .addParameter(MANIFEST_GA)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly().build();

    public InstMgrCustomPatchRemoveHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String manifestGA = MANIFEST_GA.resolveModelAttribute(context, operation).asString();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.acquireControllerLock();
                try {
                    final String translatedManifestGA = manifestGA.replace(":", "_");
                    final Path serverHome = imService.getHomeDir();
                    final Path baseTargetDir = imService.getCustomPatchDir(translatedManifestGA);

                    final MavenOptions mavenOptions = new MavenOptions(null, false);
                    final InstallationManager im = imf.create(serverHome, mavenOptions);

                    // delete any content
                    deleteDirIfExits(baseTargetDir);

                    final Collection<Channel> exitingChannels = im.listChannels();
                    for (Channel channel : exitingChannels) {
                        String name = channel.getName();
                        if (channel.getName().equals(InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX + translatedManifestGA)) {
                            im.removeChannel(name);

                            break;
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
