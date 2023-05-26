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
