package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import java.io.File;

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
import org.wildfly.core.instmgr.InstMgrCustomPatchHandler;

@CommandDefinition(name = "upload-custom-patch", description = "Uploads a custom patch Zip file to the server and subscribes the installation creating a channel to handle the patch.")
public class CustomPatchCommand extends AbstractInstMgrCommand {

    @Option(name = "custom-patch-file", required = true)
    private File customPatch;

    @Option(name = "manifest", required = true)
    private String manifestGA;

    @Override
    protected Operation buildOperation() throws CommandException {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrCustomPatchHandler.DEFINITION.getName());
        op.get(InstMgrConstants.MANIFEST).set(manifestGA);
        op.get(InstMgrConstants.CUSTOM_PATCH_FILE).set(0);
        operationBuilder.addFileAsAttachment(customPatch);

        return operationBuilder.build();
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        final CommandContext ctx = commandInvocation.getCommandContext();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            ctx.printLine("You are disconnected at the moment. Type 'connect' to connect to the server or 'help' for the list of supported commands.");
            return CommandResult.FAILURE;
        }

        this.executeOp(ctx, this.host);
        ctx.printLine("Custom Patch successfully uploaded");
        return CommandResult.SUCCESS;
    }
}
