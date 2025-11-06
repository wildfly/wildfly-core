/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;

@CommandDefinition(name = "channel-list", description = "List channels subscribed to by the installation.",
        activator = InstMgrActivator.class)
public class ChannelListCommand extends AbstractInstMgrCommand {

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        op.get(Util.OPERATION).set(Util.READ_RESOURCE);
        op.get(Util.INCLUDE_RUNTIME).set(true);

        return OperationBuilder.create(op, true).build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        ModelNode response = this.executeOp(commandInvocation.getCommandContext(), this.host);
        ModelNode result = response.get(Util.RESULT);
        List<ModelNode> channelsMn = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();

        if (channelsMn.isEmpty()) {
            ctx.printLine("No channels have been configured for this installation.");

            return CommandResult.SUCCESS;
        }

        ctx.printLine("-------");
        for (ModelNode channel : channelsMn) {
            ctx.printLine("# " + channel.get(InstMgrConstants.CHANNEL_NAME).asString());
            String manifest;
            if (channel.get(InstMgrConstants.MANIFEST).isDefined()) {
                ModelNode manifestMn = channel.get(InstMgrConstants.MANIFEST);
                if (manifestMn.get(InstMgrConstants.MANIFEST_GAV).isDefined()) {
                    manifest = manifestMn.get(InstMgrConstants.MANIFEST_GAV).asString();
                } else {
                    manifest = manifestMn.get(InstMgrConstants.MANIFEST_URL).asString();
                }
                String currentVersion = manifestMn.get(InstMgrConstants.MANIFEST_CURRENT_VERSION).asStringOrNull();
                if (currentVersion == null) {
                    ctx.printLine(String.format("  manifest: %s", manifest));
                } else {
                    ctx.printLine(String.format("  manifest: %s (version: %s)", manifest, currentVersion));
                }
            }

            if (channel.get(InstMgrConstants.REPOSITORIES).isDefined()) {
                ctx.printLine("  repositories:");
                List<ModelNode> repositoriesMn = channel.get(InstMgrConstants.REPOSITORIES).asListOrEmpty();
                for (ModelNode repository : repositoriesMn) {
                    ctx.printLine("    id: " + repository.get(InstMgrConstants.REPOSITORY_ID).asString());
                    ctx.printLine("    url: " + repository.get(InstMgrConstants.REPOSITORY_URL).asString());
                }
            }
            ctx.printLine("-------");
        }
        return CommandResult.SUCCESS;
    }

    /**
     * Returns a Set with the current channel names available on the server installation
     *
     * @param ctx
     * @param host
     * @return
     * @throws CommandException
     */
    Set<String> getAllChannelNames(CommandContext ctx, String host) throws CommandException {
        final Set<String> existingChannelNames = new HashSet<>();
        ModelNode listCmdResponse = this.executeOp(ctx, host);
        if (listCmdResponse.hasDefined(RESULT)) {
            ModelNode result = listCmdResponse.get(RESULT);
            List<ModelNode> channels = result.get(InstMgrConstants.CHANNELS).asListOrEmpty();
            for (ModelNode channel : channels) {
                existingChannelNames.add(channel.get(NAME).asString());
            }
        }
        return existingChannelNames;
    }
}
