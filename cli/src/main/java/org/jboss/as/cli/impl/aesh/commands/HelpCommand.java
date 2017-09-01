/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl.aesh.commands;

import org.jboss.as.cli.impl.aesh.commands.plugins.CommandsListAvailable;
import java.io.IOException;
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
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.handlers.GenericTypeOperationHandler;
import org.jboss.as.cli.impl.aesh.HelpSupport;
import org.jboss.as.cli.impl.aesh.CLICommandRegistry;
import org.jboss.as.cli.impl.aesh.commands.operation.LegacyCommandContainer.LegacyCommand;
import org.jboss.as.cli.impl.aesh.commands.operation.OperationCommandContainer;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
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

            List<String> allExposed = new ArrayList<>(cmd.aeshRegistry.getExposedCommands());
            List<String> candidates = new ArrayList<>();
            if (mainCommand == null) {
                // need to add all aesh and handler commands
                allExposed.add("/");
                allExposed.add(":");
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
                    // XXX OK, no command. CommadHandlers have no sub command.
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

    @Arguments(completer = HelpCompleter.class, activator = ArgActivator.class)//, valueSeparator = ',')
    private List<String> command;

    private final CLICommandRegistry aeshRegistry;

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean commands;

    public HelpCommand(CLICommandRegistry aeshRegistry) {
        this.aeshRegistry = aeshRegistry;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        if (command == null || command.isEmpty()) {
            if (commands) {
                new CommandsListAvailable(aeshRegistry).execute(commandInvocation);
            } else {
                ctx.println(commandInvocation.getHelpInfo("help"));
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
            ctx.println(getOperationHelp(builder.toString(), commandInvocation.getCommandContext()));
            return CommandResult.SUCCESS;
        }

        try {
            CommandLineParser parser = aeshRegistry.findCommand(mainCommand, builder.toString());
            // Special case for generic command that generates the help content on the fly.
            if (parser.getProcessedCommand().getCommand() instanceof LegacyCommand) {
                CommandHandler handler = ((LegacyCommand) parser.getProcessedCommand().getCommand()).getCommandHandler();
                if (handler instanceof GenericTypeOperationHandler) {
                    try {
                        ((GenericTypeOperationHandler) handler).printDescription(commandInvocation.getCommandContext());
                    } catch (CommandLineException ex1) {
                        throw new CommandException(ex1);
                    }
                    // We can only rely on handler, handler hides the actual file path
                    // to its help. eg: remove-batch-line is actually batch-remove-line.txt
                } else if (handler instanceof CommandHandlerWithHelp) {
                    try {
                        ((CommandHandlerWithHelp) handler).printHelp(commandInvocation.getCommandContext());
                    } catch (CommandLineException ex1) {
                        throw new CommandException(ex1);
                    }
                }
            } else {
                ctx.println(parser.printHelp());
            }
        } catch (CommandNotFoundException ex) {
            throw new CommandException("Command " + builder.toString() + " has no help");
        }
        return CommandResult.SUCCESS;
    }

    private static final DefaultCallbackHandler LINE = new DefaultCallbackHandler(false);

    private static String getOperationHelp(String op, CommandContext ctx) {
        if (ctx.getModelControllerClient() == null) {
            return HelpSupport.printHelp(ctx, "wildfly_raw_op");
        }

        // Check if the op exists.
        LINE.reset();
        try {
            LINE.parse(ctx.getCurrentNodePath(),
                    op, ctx);
        } catch (CommandFormatException ex) {
            return HelpSupport.printHelp(ctx, "wildfly_raw_op");
        }

        String opName = LINE.getOperationName();
        if (opName == null) {
            return HelpSupport.printHelp(ctx, "wildfly_raw_op");
        }

        OperationRequestAddress address = LINE.getAddress();
        ModelNode request = new ModelNode();
        if (address == null || address.isEmpty()) {
            request.get(Util.ADDRESS).setEmptyList();
        } else {
            if (address.endsOnType()) {
                return HelpSupport.printHelp(ctx, "wildfly_raw_op");
            }
            final ModelNode addrNode = request.get(Util.ADDRESS);
            for (OperationRequestAddress.Node node : address) {
                addrNode.add(node.getType(), node.getName());
            }
        }
        request.get(org.jboss.as.cli.Util.OPERATION).set(org.jboss.as.cli.Util.READ_OPERATION_DESCRIPTION);
        request.get(org.jboss.as.cli.Util.NAME).set(opName);
        ModelNode result;
        try {
            result = ctx.getModelControllerClient().execute(request);
        } catch (IOException e) {
            return HelpSupport.printHelp(ctx, "wildfly_raw_op");
        }
        if (!result.hasDefined(org.jboss.as.cli.Util.RESULT)) {
            return HelpSupport.printHelp(ctx, "wildfly_raw_op");
        }
        String content = HelpSupport.printHelp(ctx, result.get(org.jboss.as.cli.Util.RESULT), address);
        if (content == null) {
            return HelpSupport.printHelp(ctx, "wildfly_raw_op");
        }
        return content;
    }

}
