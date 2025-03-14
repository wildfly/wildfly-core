/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.List;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrHistoryHandler;
import org.wildfly.core.instmgr.InstMgrHistoryRevisionHandler;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.ChannelChange;

@CommandDefinition(name = "history", description = "List previous installation states.", activator = InstMgrActivator.class)
public class HistoryCommand extends AbstractInstMgrCommand {
    @Option(name = "revision")
    private String revision;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        ModelNode response = this.executeOp(ctx, this.host);
        ModelNode result = response.get(RESULT);
        if (revision != null) {
            List<ModelNode> artifactChanges = result.get(InstMgrConstants.HISTORY_RESULT_DETAILED_ARTIFACT_CHANGES).asListOrEmpty();
            List<ModelNode> channelChanges = result.get(InstMgrConstants.HISTORY_RESULT_DETAILED_CHANNEL_CHANGES).asListOrEmpty();

            if (!artifactChanges.isEmpty()) {
                ctx.printLine("Artifact Updates:");
                int maxLength = 0;
                for (ModelNode artifactChange : artifactChanges) {
                    String channelName = artifactChange.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString();
                    maxLength = maxLength < channelName.length() ? channelName.length() : maxLength;
                }
                maxLength += 2;
                for (ModelNode artifactChange : artifactChanges) {
                    String status = artifactChange.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_STATUS).asString();
                    String channelName = artifactChange.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NAME).asString();
                    String oldVersion = artifactChange.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_OLD_VERSION).asStringOrNull();
                    String newVersion = artifactChange.get(InstMgrConstants.HISTORY_DETAILED_ARTIFACT_NEW_VERSION).asStringOrNull();

                    if (status.equals(ArtifactChange.Status.UPDATED.name().toLowerCase())) {
                        ctx.printLine(String.format(" %1$-" + maxLength + "s %2$15s ==> %3$-15s", channelName, oldVersion, newVersion));
                    } else if (status.equals(ArtifactChange.Status.REMOVED.name().toLowerCase())) {
                        ctx.printLine(String.format(" %1$-" + maxLength + "s %2$15s ==> []", channelName, oldVersion));
                    } else if (status.equals(ArtifactChange.Status.INSTALLED.name().toLowerCase())) {
                        ctx.printLine(String.format(" %1$-" + maxLength + "s [] ==> %2$-15s", channelName, newVersion));
                    }
                }
                ctx.printLine("");
            }

            if (!channelChanges.isEmpty()) {
                ctx.printLine("Configuration changes:");
                for (ModelNode channelChange : channelChanges) {
                    String status = channelChange.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_STATUS).asString();
                    String channelName = channelChange.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NAME).asString();

                    if (status.equals(ChannelChange.Status.MODIFIED.name().toLowerCase())) {
                        ctx.printLine(String.format("[Updated channel] %s", channelName));
                    } else if (status.equals(ChannelChange.Status.REMOVED.name().toLowerCase())) {
                        ctx.printLine(String.format("[Removed channel] %s", channelName));
                    } else if (status.equals(ChannelChange.Status.ADDED.name().toLowerCase())) {
                        ctx.printLine(String.format("[Added channel] %s", channelName));
                    }

                    String oldManifest = channelChange.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_MANIFEST).asStringOrNull();
                    String newManifest = channelChange.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_MANIFEST, InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_MANIFEST).asStringOrNull();
                    oldManifest = oldManifest == null ? "[]" : oldManifest;
                    newManifest = newManifest == null ? "[]" : newManifest;
                    ctx.printLine(String.format("\tManifest:\t%s ==> %s", oldManifest, newManifest));

                    ctx.printLine("\tRepositories:");
                    List<ModelNode> repositories = channelChange.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_REPOSITORIES).asList();
                    for (ModelNode repository : repositories) {
                        String oldRepo = repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY).asStringOrNull();
                        String newRepo = repository.get(InstMgrConstants.HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY).asStringOrNull();
                        oldRepo = oldRepo == null ? "[]" : oldRepo;
                        newRepo = newRepo == null ? "[]" : newRepo;
                        ctx.printLine(String.format("\t\t%s ==> %s", oldRepo, newRepo));
                    }
                }
            }

        } else {
            List<ModelNode> results = result.asListOrEmpty();
            for (ModelNode resultMn : results) {
                String hash = resultMn.get(InstMgrConstants.HISTORY_RESULT_HASH).asString();
                String timeStamp = resultMn.get(InstMgrConstants.HISTORY_RESULT_TIMESTAMP).asString();
                String type = resultMn.get(InstMgrConstants.HISTORY_RESULT_TYPE).asString();
                final List<String> versions = resultMn.get(InstMgrConstants.HISTORY_RESULT_CHANNEL_VERSIONS).asListOrEmpty().stream().map(ModelNode::asString).collect(Collectors.toList());
                String description;
                if (versions.isEmpty()) {
                    description = resultMn.get(InstMgrConstants.HISTORY_RESULT_DESCRIPTION).asStringOrNull();
                    description = description == null ? "[]" : description;
                } else {
                    description = "[" + String.join(" + ", versions) + "]";
                }
                ctx.printLine(String.format("[%s] %s - %s %s", hash, timeStamp, type, description));
            }
        }

        return CommandResult.SUCCESS;
    }

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        if (revision != null) {
            op.get(OP).set(InstMgrHistoryRevisionHandler.DEFINITION.getName());
            op.get(InstMgrConstants.REVISION).set(revision);
        } else {
            op.get(OP).set(InstMgrHistoryHandler.DEFINITION.getName());
        }

        return OperationBuilder.create(op).build();
    }
}
