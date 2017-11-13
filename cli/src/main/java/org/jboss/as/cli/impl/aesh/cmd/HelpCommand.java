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
package org.jboss.as.cli.impl.aesh.cmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aesh.command.option.Arguments;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.activator.CommandActivator;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.handlers.GenericTypeOperationHandler;
import org.jboss.as.cli.impl.aesh.HelpSupport;
import org.jboss.as.cli.impl.aesh.CLICommandRegistry;
import org.jboss.as.cli.impl.aesh.cmd.operation.OperationCommandContainer;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;

/**
 * The help command. Allows to display help for all kind of commands and
 * operations.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "help", description = "", aliases = {"h"})
public class HelpCommand implements Command<CLICommandInvocation> {

    public static class HelpCompleter implements OptionCompleter<CLICompleterInvocation> {

        private final DefaultCallbackHandler parsedCmd = new DefaultCallbackHandler(false);

        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            HelpCommand cmd = (HelpCommand) completerInvocation.getCommand();
            String mainCommand = null;
            if (cmd.command != null) {
                if (cmd.command.size() > 1) {
                    // Nothing to add.
                    return;
                }
                mainCommand = cmd.command.get(0);
            }

            // Special case for operations.
            String buff = completerInvocation.getGivenCompleteValue();
            if (OperationCommandContainer.isOperation(buff)) {
                List<String> candidates = new ArrayList<>();
                parsedCmd.reset();
                try {
                    parsedCmd.parse(null,
                            buff, false,
                            completerInvocation.getCommandContext());
                } catch (CommandFormatException ex) {
                    // XXX OK.
                    return;
                }
                int offset = OperationRequestCompleter.INSTANCE.
                        complete(completerInvocation.getCommandContext(),
                                parsedCmd,
                                buff,
                                0, candidates);
                boolean contains = false;
                for (String c : candidates) {
                    int i = c.indexOf("(");
                    if (i > 0) {
                        contains = true;
                    }
                }
                if (contains) {
                    return;
                } else {
                    Collections.sort(candidates);
                    completerInvocation.setOffset(buff.length() - offset);
                    completerInvocation.addAllCompleterValues(candidates);
                    completerInvocation.setAppendSpace(false);
                }
                return;
            }

            List<String> allExposed = new ArrayList<>(cmd.aeshRegistry.getAllCommandNames());
            List<String> candidates = new ArrayList<>();
            if (mainCommand == null) {
                if (completerInvocation.getCommandContext().getModelControllerClient() != null) {
                    allExposed.add("/");
                    allExposed.add(":");
                }
                if (buff == null || buff.isEmpty()) {
                    candidates.addAll(allExposed);
                } else {
                    for (String c : allExposed) {
                        if (c.startsWith(buff)) {
                            candidates.add(c);
                        }
                    }
                }
            } else {
                try {
                    CommandLineParser<? extends Command> p = cmd.aeshRegistry.findCommand(mainCommand, null);
                    for (CommandLineParser child : p.getAllChildParsers()) {
                        if (child.getProcessedCommand().name().startsWith(buff)) {
                            candidates.add(child.getProcessedCommand().name());
                        }
                    }
                } catch (CommandNotFoundException ex) {
                    // XXX OK, no command. CommandHandler has no sub command.
                }
            }

            Collections.sort(candidates);
            completerInvocation.addAllCompleterValues(candidates);
        }

    }

    public static class ArgActivator extends AbstractRejectOptionActivator {

        public ArgActivator() {
            super("commands");
        }
    }

    @Arguments(completer = HelpCompleter.class, activator = ArgActivator.class)
    private List<String> command;

    private final CLICommandRegistry aeshRegistry;

    @Option(hasValue = false)
    private boolean commands;

    public HelpCommand(CLICommandRegistry aeshRegistry) {
        this.aeshRegistry = aeshRegistry;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        if (command == null || command.isEmpty()) {
            if (commands) {
                return listAvailable(commandInvocation);
            } else {
                ctx.printLine(commandInvocation.getHelpInfo("help"));
            }
            return CommandResult.SUCCESS;
        }
        if (commands) {
            throw new CommandException("commands option not usable with an argument");
        }
        if (command.size() > 2) {
            throw new CommandException("Command has more than one action");
        }
        String mainCommand = command.get(0);
        StringBuilder builder = new StringBuilder();
        for (String s : command) {
            builder.append(s).append(" ");
        }

        // An operation?
        if (OperationCommandContainer.isOperation(mainCommand)) {
            try {
                ctx.printLine(getOperationHelp(builder.toString(), commandInvocation.getCommandContext()));
            } catch (Exception ex) {
                ctx.printLine("Error getting operation help: " + ex.getLocalizedMessage());
            }
            return CommandResult.SUCCESS;
        }

        try {
            CommandLineParser parser = aeshRegistry.findCommand(mainCommand, builder.toString());
            ctx.printLine(parser.printHelp());
        } catch (CommandNotFoundException ex) {
            CommandHandler handler = aeshRegistry.getCommandHandler(mainCommand);
            if (handler instanceof GenericTypeOperationHandler) {
                try {
                    ((GenericTypeOperationHandler) handler).printDescription(commandInvocation.getCommandContext());
                } catch (CommandLineException ex1) {
                    throw new CommandException(ex1);
                }
            } else if (handler instanceof CommandHandlerWithHelp) {
                try {
                    ((CommandHandlerWithHelp) handler).displayHelp(commandInvocation.getCommandContext());
                } catch (CommandLineException ex1) {
                    throw new CommandException(ex1);
                }
            } else {
                throw new CommandException("Command " + builder.toString() + " has no help");
            }
        }
        return CommandResult.SUCCESS;
    }

    private static final DefaultCallbackHandler LINE = new DefaultCallbackHandler(false);
    private static final String OP_FALLBACK_HELP = "wildfly_cli_raw_op";

    private static String getOperationHelp(String op, CommandContext ctx) throws Exception {
        if (ctx.getModelControllerClient() == null) {
            throw new Exception("Not connected.");
        }

        // Check if the op exists.
        LINE.reset();
        try {
            LINE.parse(ctx.getCurrentNodePath(),
                    op, ctx);
        } catch (CommandFormatException ex) {
            throw new Exception(HelpSupport.printHelp(ctx, OP_FALLBACK_HELP));
        }

        String opName = LINE.getOperationName();
        if (opName == null) {
            throw new Exception("No operation name.");
        }

        OperationRequestAddress address = LINE.getAddress();
        ModelNode request = new ModelNode();
        if (address == null || address.isEmpty()) {
            request.get(Util.ADDRESS).setEmptyList();
        } else {
            if (address.endsOnType()) {
                throw new Exception("Invalid address.");
            }
            final ModelNode addrNode = request.get(Util.ADDRESS);
            for (OperationRequestAddress.Node node : address) {
                addrNode.add(node.getType(), node.getName());
            }
        }
        request.get(org.jboss.as.cli.Util.OPERATION).set(org.jboss.as.cli.Util.READ_OPERATION_DESCRIPTION);
        request.get(org.jboss.as.cli.Util.NAME).set(opName);
        ModelNode result;

        result = ctx.getModelControllerClient().execute(request);

        if (!result.hasDefined(org.jboss.as.cli.Util.RESULT)) {
            throw new Exception("Error retrieving operation description.");
        }
        String content = HelpSupport.printHelp(ctx, result.get(org.jboss.as.cli.Util.RESULT), address);
        if (content == null) {
            throw new Exception("Error retrieving operation description.");
        }
        return content;
    }

    private CommandResult listAvailable(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();

        List<String> lst = new ArrayList<>();
        // First aesh
        for (String c : aeshRegistry.getAllCommandNames()) {
            CommandLineParser<? extends Command> cmdParser;
            try {
                cmdParser = aeshRegistry.findCommand(c, null);
            } catch (CommandNotFoundException ex) {
                continue;
            }
            CommandActivator activator = cmdParser.getProcessedCommand().getActivator();
            if (activator == null || activator.isActivated(cmdParser.getProcessedCommand())) {
                if (cmdParser.isGroupCommand()) {
                    for (CommandLineParser child : cmdParser.getAllChildParsers()) {
                        CommandActivator childActivator = child.getProcessedCommand().getActivator();
                        if (childActivator == null
                                || childActivator.isActivated(child.getProcessedCommand())) {
                            lst.add(c + " " + child.getProcessedCommand().name());
                        }
                    }
                } else {
                    lst.add(c);
                }
            }
        }
        // Then handler
        for (String cmd : aeshRegistry.getTabCompletionCommands()) {
            CommandHandler handler = aeshRegistry.getCommandHandler(cmd);
            if (handler.isAvailable(ctx)) {
                lst.add(cmd);
            }
        }
        ctx.printLine("Commands available in the current context:");
        print(lst, ctx);
        ctx.printLine("To read a description of a specific command execute 'help <command name>'.");

        return CommandResult.SUCCESS;
    }

    private void print(List<String> lst, CommandContext ctx) {
        lst.sort(null);
        ctx.printColumns(lst);
    }
}
