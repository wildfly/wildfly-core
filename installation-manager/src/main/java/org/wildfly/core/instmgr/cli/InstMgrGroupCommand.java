/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        CustomPatchRemoveCommand.class
}, activator = InstMgrActivator.class)
public class InstMgrGroupCommand implements Command<CLICommandInvocation> {
    public static final String COMMAND_NAME = "installer";

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        throw new CommandException("Command action is missing.");
    }
}
