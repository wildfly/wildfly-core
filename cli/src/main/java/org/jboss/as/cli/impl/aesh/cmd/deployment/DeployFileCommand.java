/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.NameActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.UnmanagedActivator;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;

/**
 * Deploy a file. All fields are public to be accessible from legacy commands.
 * To be made private when legacies are removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "deploy-file", description = "", activator = ControlledCommandActivator.class)
public class DeployFileCommand extends AbstractDeployContentCommand {

    @Option(hasValue = false, activator = UnmanagedActivator.class, required = false)
    public boolean unmanaged;

    @Option(hasValue = true, required = false, completer
            = EnableCommand.NameCompleter.class,
            activator = NameActivator.class)
    public String name;

    // Argument comes first, aesh behavior.
    @Argument(required = true)
    public File file;

    public DeployFileCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, permissions);
    }

    @Deprecated
    public DeployFileCommand(CommandContext ctx, String replaceName) {
        super(ctx, null, replaceName);
    }

    @Override
    protected void checkArgument() throws CommandException {
        if (file == null) {
            throw new CommandException("No deployment file");
        }
        if (!file.exists()) {
            throw new CommandException("Path " + file.getAbsolutePath()
                    + " doesn't exist.");
        }
        if (!unmanaged && file.isDirectory()) {
            throw new CommandException(file.getAbsolutePath() + " is a directory.");
        }
    }

    @Override
    protected String getName() {
        if (name != null) {
            return name;
        }
        return file.getName();
    }

    @Override
    protected void addContent(CommandContext context, ModelNode content) throws OperationFormatException {
        if (unmanaged) {
            content.get(Util.PATH).set(file.getAbsolutePath());
            content.get(Util.ARCHIVE).set(file.isFile());
        } else if (context.getBatchManager().isBatchActive()) {
            Attachments attachments = context.getBatchManager().getActiveBatch().getAttachments();
            int index = attachments.addFileAttachment(file.getAbsolutePath());
            content.get(Util.INPUT_STREAM_INDEX).set(index);
        } else {
            content.get(Util.INPUT_STREAM_INDEX).set(0);
        }
    }

    @Override
    protected List<String> getServerGroups(CommandContext ctx)
            throws CommandFormatException {
        return DeploymentCommand.getServerGroups(ctx, ctx.getModelControllerClient(),
                allServerGroups, serverGroups, file);
    }

    @Override
    protected String getCommandName() {
        return "deploy-file";
    }

    @Override
    protected ModelNode execute(CommandContext ctx, ModelNode request)
            throws IOException {
        ModelNode result;
        if (!unmanaged) {
            OperationBuilder op = new OperationBuilder(request);
            op.addFileAsAttachment(file);
            request.get(Util.CONTENT).get(0).get(Util.INPUT_STREAM_INDEX).set(0);
            try (Operation operation = op.build()) {
                result = ctx.getModelControllerClient().execute(operation);
            }
        } else {
            result = ctx.getModelControllerClient().execute(request);
        }
        return result;
    }
}
