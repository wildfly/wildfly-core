/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * The connect command. Allows a management connection to a local
 * or remote WildFly or JBoss EAP instance.
 *
 * @author <a href="ingo@redhat.com">Ingo Weiss</a>
 */
@CommandDefinition(name = "connect", description = "Connects to a local or remote instance")
public class ConnectCommand implements Command<CLICommandInvocation> {

    @Argument
    private String controllerUrl;

    @Option(name = "bind")
    private String bindAddress;

    @Option(hasValue = false, name = "help")
    private boolean help;


    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext commandContext = commandInvocation.getCommandContext();

        if (help) {
            commandContext.printLine(commandInvocation.getHelpInfo("connect"));
            return CommandResult.SUCCESS;
        }

        try {
            commandContext.connectController(controllerUrl, bindAddress);
        } catch (CommandLineException e) {
            throw new CommandException(Util.getMessagesFromThrowable(e));
        }

        return CommandResult.SUCCESS;
    }
}
