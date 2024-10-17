/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommandDefinition;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

@GroupCommandDefinition(name = InstMgrGroupCommand.COMMAND_NAME, description = "", groupCommands = {
        UpdateCommand.class,
        CleanCommand.class,
        RevertCommand.class,
        HistoryCommand.class,
        ChannelListCommand.class,
        ChannelAddCommand.class,
        ChannelEditCommand.class,
        ChannelRemoveCommand.class,
        CustomPatchUploadCommand.class,
        CustomPatchRemoveCommand.class,
        ListCertificatesCommand.class,
        AddCertificatesCommand.class,
        RemoveCertificatesCommand.class
}, activator = InstMgrActivator.class)
public class InstMgrGroupCommand implements Command<CLICommandInvocation> {
    public static final String COMMAND_NAME = "installer";

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        throw new CommandException("Command action is missing.");
    }
}
