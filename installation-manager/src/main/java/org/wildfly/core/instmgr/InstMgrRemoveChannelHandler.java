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
import java.util.HashSet;
import java.util.Set;

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
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Unsubscribes the installation from a channel by removing the channel.
 */
public class InstMgrRemoveChannelHandler extends InstMgrOperationStepHandler {

    public static final String OPERATION_NAME = "channel-remove";

    private static final AttributeDefinition CHANNEL_NAME = new SimpleAttributeDefinitionBuilder(InstMgrConstants.CHANNEL_NAME, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .addParameter(CHANNEL_NAME)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    InstMgrRemoveChannelHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String channel = CHANNEL_NAME.resolveModelAttribute(context, operation).asStringOrNull();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                try {
                    Path serverHome = imService.getHomeDir();
                    MavenOptions mavenOptions = new MavenOptions(null, false);
                    InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                    final Collection<Channel> exitingChannels = installationManager.listChannels();
                    final Set<String> exitingChannelNames = new HashSet<>();
                    for (Channel c : exitingChannels) {
                        exitingChannelNames.add(c.getName());
                    }

                    if (!exitingChannelNames.contains(channel)) {
                        throw InstMgrLogger.ROOT_LOGGER.channelNameNotFound(channel);
                    }

                    installationManager.removeChannel(channel);

                } catch (OperationFailedException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
