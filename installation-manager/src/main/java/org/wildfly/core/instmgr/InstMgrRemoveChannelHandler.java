/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
        final String channel = CHANNEL_NAME.resolveModelAttribute(context, operation).asString();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.acquireControllerLock();
                try {
                    Path serverHome = imService.getHomeDir();
                    MavenOptions mavenOptions = new MavenOptions(null, false);
                    InstallationManager installationManager = imf.create(serverHome, mavenOptions);

                    final Collection<Channel> existingChannels = installationManager.listChannels();
                    boolean found = false;
                    for (Channel c : existingChannels) {
                        if (channel.equals(c.getName())) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        throw InstMgrLogger.ROOT_LOGGER.channelNameNotFound(channel);
                    }

                    installationManager.removeChannel(channel);

                } catch (OperationFailedException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
