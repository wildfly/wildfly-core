/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_DESCRIPTION;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_FINGERPRINT;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_KEY_ID;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_STATUS;

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

@CommandDefinition(name = "certificates-list", description = "List trusted component certificates.", activator = InstMgrActivator.class)
public class ListCertificatesCommand extends AbstractInstMgrCommand {

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
        List<ModelNode> certificatesMn = result.get(InstMgrConstants.CERTIFICATES).asListOrEmpty();

        if (certificatesMn.isEmpty()) {
            ctx.printLine("No component certificates have been trusted for this installation.");

            return CommandResult.SUCCESS;
        }

        ctx.printLine("-------");
        for (ModelNode certificate : certificatesMn) {
            ctx.printLine("key ID " + certificate.get(CERT_KEY_ID).asString());
            ctx.printLine("fingerprint " + certificate.get(CERT_FINGERPRINT).asString());
            ctx.printLine("description " + certificate.get(CERT_DESCRIPTION).asString());
            ctx.printLine("status " + certificate.get(CERT_STATUS).asString());
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
