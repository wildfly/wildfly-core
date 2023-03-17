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
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler that cleans the installation manager work directories or remove a custom patch.
 */
public class InstMgrCleanHandler extends InstMgrOperationStepHandler {
    static final String OPERATION_NAME = "clean";
    protected static final AttributeDefinition LIST_UPDATES_WORK_DIR = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.LIST_UPDATES_WORK_DIR, ModelType.STRING)
            .setRequired(false)
            .setAlternatives(InstMgrConstants.CUSTOM_PATCH)
            .setStorageRuntime()
            .build();

    protected static final AttributeDefinition CUSTOM_PATCH = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.CUSTOM_PATCH, ModelType.BOOLEAN)
            .setRequired(false)
            .setAlternatives(InstMgrConstants.LIST_UPDATES_WORK_DIR)
            .setStorageRuntime()
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .addParameter(LIST_UPDATES_WORK_DIR)
            .setRuntimeOnly()
            .build();

    InstMgrCleanHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String listUpdatesWorkDir = LIST_UPDATES_WORK_DIR.resolveModelAttribute(context, operation).asStringOrNull();
        final boolean customPatch = CUSTOM_PATCH.resolveModelAttribute(context, operation).asBoolean(false);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    if (customPatch) {
                        final Path serverHome = imService.getHomeDir();
                        final Path baseTargetDir = imService.getCustomPatchDir();

                        final MavenOptions mavenOptions = new MavenOptions(null, false);
                        final InstallationManager im = imf.create(serverHome, mavenOptions);

                        // delete any content
                        deleteDirIfExits(baseTargetDir);

                        final Collection<Channel> exitingChannels = im.listChannels();
                        for (Channel channel : exitingChannels) {
                            String name = channel.getName();
                            if (channel.getName().equals(InstMgrConstants.DEFAULT_CUSTOM_CHANNEL_NAME)) {
                                im.removeChannel(name);

                                break;
                            }
                        }
                    } else if (listUpdatesWorkDir != null) {
                        imService.deleteTempDir(listUpdatesWorkDir);
                    } else {
                        deleteDirIfExits(imService.getPreparedServerDir());
                        imService.deleteTempDirs();
                        imService.resetCandidateStatus();
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
