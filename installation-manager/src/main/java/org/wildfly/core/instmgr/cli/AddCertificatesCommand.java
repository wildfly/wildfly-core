/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.wildfly.core.instmgr.InstMgrConstants.CERT_FILE;

import java.io.File;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.readline.Prompt;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.instmgr.InstMgrCertificateImportHandler;
import org.wildfly.core.instmgr.InstMgrCertificateParseHandler;
import org.wildfly.core.instmgr.InstMgrConstants;

@CommandDefinition(name = "certificates-add", description = "Adds a trusted component certificate.", activator = InstMgrActivator.class)
public class AddCertificatesCommand extends AbstractInstMgrCommand {

    @Option(name = CERT_FILE, required = true)
    private File certFile;

    @Option(name = "non-interactive")
    private boolean nonInteractive;

    @Override
    protected Operation buildOperation() {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrCertificateImportHandler.DEFINITION.getName());
        op.get(CERT_FILE).set(0);
        operationBuilder.addFileAsAttachment(certFile);

        return operationBuilder.build();
    }

    protected Operation buildParseOperation() {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrCertificateParseHandler.DEFINITION.getName());
        op.get("cert-file").set(0);
        operationBuilder.addFileAsAttachment(certFile);

        return operationBuilder.build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();

        final ModelNode modelNode = this.executeOp(buildParseOperation(), ctx, this.host).get(RESULT);

        commandInvocation.println("Components signed with following certificate will be considered trusted:");
        commandInvocation.println("key-id: " + modelNode.get(InstMgrConstants.CERT_KEY_ID));
        commandInvocation.println("fingerprint: " + modelNode.get(InstMgrConstants.CERT_FINGERPRINT));
        commandInvocation.println("description: " + modelNode.get(InstMgrConstants.CERT_DESCRIPTION));

        final String input = commandInvocation.inputLine(new Prompt(String.format("Import this certificate y/N")));
        if (nonInteractive || "y".equals(input)) {
            commandInvocation.print("Importing a trusted certificate");
        } else {
            commandInvocation.print("Importing canceled.");
        }

        this.executeOp(ctx, this.host);

        return CommandResult.SUCCESS;
    }
}
