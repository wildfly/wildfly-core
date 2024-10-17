/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_KEY_ID;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrCertificateRemoveHandler;

@CommandDefinition(name = "certificates-remove", description = "Removes a trusted component certificate.", activator = InstMgrActivator.class)
public class RemoveCertificatesCommand extends AbstractInstMgrCommand {

    @Option(name = CERT_KEY_ID, required = true)
    private String keyId;
    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();

        op.get(OP).set(InstMgrCertificateRemoveHandler.DEFINITION.getName());
        op.get(CERT_KEY_ID).set(keyId);

        return OperationBuilder.create(op).build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();

        this.executeOp(ctx, this.host);

        return CommandResult.SUCCESS;
    }
}
