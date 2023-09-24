/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.util.List;
import java.util.Set;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrConstants;

@CommandDefinition(name = "channel-edit", description = "Edit a channel subscription.", activator = InstMgrActivator.class)
public class ChannelEditCommand extends AbstractInstMgrCommand {
    @Option(name = "channel-name", required = true)
    String channelName;
    @Option(name = "manifest", required = true)
    String manifest;
    @OptionList(name = "repositories", required = true)
    private List<String> repositories;

    @Override
    protected Operation buildOperation() throws CommandException {
        final ModelNode op = new ModelNode();
        op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        op.get(NAME).set(InstMgrConstants.CHANNELS);

        ModelNode channel = new ModelNode();
        channel.get(NAME).set(channelName);

        addManifestToModelNode(channel, manifest);
        addRepositoriesToModelNode(channel, repositories);

        ModelNode channels = new ModelNode().addEmptyList();
        channels.add(channel);
        op.get(VALUE).set(channels);
        return OperationBuilder.create(op).build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        // we only have available a single :write-attribute as server management Operation.
        // However, at client side we want to have a channel-add / channel-edit, so we need first read the available
        // channels to validate channel-add / channel-edit operations
        Set<String> allChannelNames = new ChannelListCommand().getAllChannelNames(ctx, host);
        if (!allChannelNames.contains(channelName)) {
            throw new CommandException(String.format("Channel '%s' is not present.", channelName));
        }

        this.executeOp(commandInvocation.getCommandContext(), this.host);
        ctx.printLine("Channel '" + channelName + "' has been modified.");
        return CommandResult.SUCCESS;
    }
}
