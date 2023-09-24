/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractCommandActivator;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@GroupCommandDefinition(name = "patch", description = "", groupCommands
        = {PatchApply.class, PatchRollback.class, PatchHistory.class,
            PatchInfo.class, PatchInspect.class,}, activator = PatchCommand.PatchCommandActivator.class)
public class PatchCommand implements Command<CLICommandInvocation> {

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.getCommandContext().printLine(commandInvocation.getHelpInfo("patch"));
            return CommandResult.SUCCESS;
        }
        throw new CommandException("Command action is missing.");
    }

    /**
     * Hides the high level patch commands on a standalone installation only.
     */
    public static class PatchCommandActivator extends AbstractCommandActivator {
        @Override
        public boolean isActivated(ParsedCommand command) {
            CommandContext commandContext = this.getCommandContext();
            return commandContext.isDomainMode();
        }
    }
}
