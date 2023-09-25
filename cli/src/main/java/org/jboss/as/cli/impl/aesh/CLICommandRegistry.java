/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aesh.readline.completion.CompleteOperation;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedCommandBuilder;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.Command;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.activator.CommandActivator;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.container.AeshCommandContainer;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.impl.parser.CommandLineParserBuilder;
import org.aesh.command.impl.registry.MutableCommandRegistryImpl;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.parser.OptionParserException;
import org.aesh.command.registry.MutableCommandRegistry;
import org.aesh.parser.ParsedLine;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.aesh.cmd.operation.LegacyCommandContainer;
import org.jboss.as.cli.impl.aesh.cmd.operation.OperationCommandContainer;
import org.jboss.logging.Logger;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * Command registry that deals with both CLI Handler and aesh Command.
 *
 * @author jdenise@redhat.com
 */
public class CLICommandRegistry extends CommandRegistry implements org.aesh.command.registry.CommandRegistry {
    private static final Logger log = Logger.getLogger(CLICommandRegistry.class);
    private final MutableCommandRegistry reg = new MutableCommandRegistryImpl();
    private final AeshCommandContainerBuilder<? extends CommandInvocation> containerBuilder = new AeshCommandContainerBuilder();
    private final CommandContextImpl ctx;
    private final OperationCommandContainer op;
    public CLICommandRegistry(CommandContextImpl ctx, OperationCommandContainer op) {
        this.ctx = ctx;
        this.op = op;
    }

    @Override
    public CommandHandler remove(String cmdName) {
        removeCommand(cmdName);
        return super.remove(cmdName);
    }

    private CommandContainer addCommandContainer(CommandContainer container, boolean checkExistence) throws CommandLineException {
        CLICommandContainer cliContainer;
        try {
            if (container instanceof CLICommandContainer) {
                cliContainer = (CLICommandContainer) container;
            } else {
                cliContainer = wrapContainer(container);
            }
        } catch (OptionParserException ex) {
            throw new CommandLineException(ex);
        }

        if (checkExistence) {
            checkExistence(container.getParser().getProcessedCommand().name());
        }

        reg.addCommand(cliContainer);

        return cliContainer;
    }

    @Override
    public void registerHandler(CommandHandler handler, boolean tabComplete, String... names) throws RegisterHandlerException {
        RegisterHandlerException error = null;
        for (String name : names) {
            try {
                checkExistence(name);
            } catch (CommandLineException ex) {
                if (error == null) {
                    error = new RegisterHandlerException(name);
                } else {
                    error.addCommand(name);
                }
            }
        }
        if (error != null) {
            throw error;
        }

        super.registerHandler(handler, tabComplete, names);
        if (tabComplete) {
            try {
                addCommandContainer(new LegacyCommandContainer(ctx, names, handler), false);
            } catch (CommandLineException | CommandLineParserException ex) {
                throw new RegisterHandlerException(ex.getLocalizedMessage());
            }
        }
    }

    private void checkExistence(String name) throws RegisterHandlerException {
        try {
            reg.getCommand(name, name);
            throw new RegisterHandlerException(name);
        } catch (CommandNotFoundException ex) {
            // XXX OK expected.
            //check in commands handler.
            if (getCommandHandler(name) != null) {
                throw new RegisterHandlerException(name);
            }
        }
    }

    CLICommandContainer wrapContainer(CommandContainer commandContainer) throws OptionParserException {
        return new CLICommandContainer(commandContainer, ctx);
    }

    public CommandContainer addCommand(Command<?> command) throws CommandLineException, CommandLineParserException {
        return addCommand(command, Collections.emptyMap(), false);
    }

    public CommandContainer addThirdPartyCommand(Command<?> command, Map<String, String> renaming) throws CommandLineParserException, CommandLineException {
        return addCommand(command, renaming, true);
    }

    public CommandContainer addCommand(Command<? extends CommandInvocation> command, Map<String, String> renaming, boolean thirdparty) throws CommandLineException, CommandLineParserException {
        CommandContainer<? extends CommandInvocation> container = containerBuilder.create(command);

        // Sub command handling
        String name = container.getParser().getProcessedCommand().name();
        int index = name.indexOf("@");
        if (index >= 0 && index != name.length() - 1) {
            String parentName = name.substring(index + 1);
            String childName = name.substring(0, index);
            if (!parentName.isEmpty()) {
                try {
                    CommandLineParser existingParent = findCommand(parentName,
                            parentName);
                    // Parent exists.
                    // If child already exists, we can't register it.
                    try {
                        findCommand(parentName, parentName + " " + childName);
                        throw new CommandLineException("Command "
                                + parentName + " " + childName
                                + " already exists. Can't register " + name);
                    } catch (CommandNotFoundException ex) {
                        // XXX OK
                    }

                    try {
                        CommandLineParser<? extends CommandInvocation> parser = container.getParser();
                        ProcessedCommand<? extends Command<? extends CommandInvocation>, ? extends CommandInvocation> cmd = parser.getProcessedCommand();
                        ProcessedCommandBuilder cmdBuilder = ProcessedCommandBuilder.builder();
                        ProcessedCommand<? extends Command<? extends CommandInvocation>, ? extends CommandInvocation> cmd2 = cmdBuilder.
                                activator(cmd.getActivator()).
                                addOptions(cmd.getOptions()).
                                aliases(cmd.getAliases()).
                                arguments(cmd.getArguments()).
                                argument(cmd.getArgument()).
                                command(cmd.getCommand()).
                                description(cmd.description()).
                                name(childName).
                                populator(cmd.getCommandPopulator()).
                                resultHandler(cmd.resultHandler()).
                                validator(cmd.validator()).
                                create();

                        // Add sub to existing command.
                        container = new AeshCommandContainer(cmd2);
                        existingParent.
                                addChildParser(container.getParser());
                    } catch (CommandLineParserException ex) {
                        throw new CommandLineException(ex);
                    }
                    return getCommand(parentName, parentName + " " + childName);
                } catch (CommandNotFoundException ex) {
                    log.warn("No parent "
                            + parentName + " command found. Registering command as "
                            + name);
                }
            }
        }
        String rename = renaming.get(name);
        if (rename != null) {
            ProcessedCommand cmd = container.getParser().getProcessedCommand();
            ProcessedCommandBuilder cmdBuilder = ProcessedCommandBuilder.builder();
            container = new AeshCommandContainer(
                    CommandLineParserBuilder.builder()
                    .processedCommand(cmdBuilder.
                            activator(cmd.getActivator()).
                            addOptions(cmd.getOptions()).
                            aliases(cmd.getAliases()).
                            arguments(cmd.getArguments()).
                            argument(cmd.getArgument()).
                            command(cmd.getCommand()).
                            description(cmd.description()).
                            name(rename).
                            populator(cmd.getCommandPopulator()).
                            resultHandler(cmd.resultHandler()).
                            validator(cmd.validator()).
                            create()).create());
        }
        // Do not apply CLI specific value resolution to third party extensions.
        if (thirdparty) {
            CommandContainer convertedContainer = disableResolution(container.getParser());
            for (Object obj : container.getParser().getAllChildParsers()) {
                CommandLineParser cp = (CommandLineParser) obj;
                convertedContainer.getParser().addChildParser(disableResolution(cp).getParser());
            }
            container = convertedContainer;
        }
        return addCommand(container);
    }

    private CommandContainer disableResolution(CommandLineParser parser) throws OptionParserException, CommandLineParserException {
        ProcessedCommand cmd = parser.getProcessedCommand();
        ProcessedCommandBuilder cmdBuilder = ProcessedCommandBuilder.builder();
        CommandContainer convertedContainer = new AeshCommandContainer(
                CommandLineParserBuilder.builder()
                .processedCommand(cmdBuilder.
                        activator(cmd.getActivator()).
                        addOptions(ExpressionValueConverter.disableResolution(cmd.getOptions())).
                        aliases(cmd.getAliases()).
                        arguments(ExpressionValueConverter.disableResolution(cmd.getArguments())).
                        argument(ExpressionValueConverter.disableResolution(cmd.getArgument())).
                        command(cmd.getCommand()).
                        description(cmd.description()).
                        name(cmd.name()).
                        populator(cmd.getCommandPopulator()).
                        resultHandler(cmd.resultHandler()).
                        validator(cmd.validator()).
                        create()).create());
        return convertedContainer;
    }

    public CommandContainer addCommand(CommandContainer container) throws CommandLineException {
        return addCommandContainer(container, true);
    }

    @Override
    public CommandContainer<CLICommandInvocation> getCommand(String name, String line)
            throws CommandNotFoundException {
        if (OperationCommandContainer.isOperation(name)) {
            return op;
        }
        return reg.getCommand(name, line);
    }

    @Override
    public void completeCommandName(CompleteOperation completeOperation, ParsedLine parsedLine) {
        reg.completeCommandName(completeOperation, parsedLine);
    }

    @Override
    public Set<String> getAllCommandNames() {
        return reg.getAllCommandNames();
    }

    public void removeCommand(String name) {
        reg.removeCommand(name);
    }

    public CommandLineParser findCommand(String name, String line) throws CommandNotFoundException {
        CommandContainer c = getCommand(name, line);
        CommandLineParser p = c.getParser();
        String[] split = line == null ? new String[0] : line.split(" ");
        if (split.length > 1) {
            String sub = split[1];
            CommandLineParser child = c.getParser().getChildParser(sub);
            if (child != null) {
                // Must make it a CLI thing to properly print the help.
                if (c instanceof CLICommandContainer) {
                    CLICommandContainer cli = (CLICommandContainer) c;
                    child = cli.wrapParser(child);
                }
                p = child;
            } else {
                throw new CommandNotFoundException("Command not found " + line, name);
            }
        }
        return p;
    }

    @Override
    public CommandContainer getCommandByAlias(String alias) throws CommandNotFoundException {
        return reg.getCommandByAlias(alias);
    }

    @Override
    public List<CommandLineParser<?>> getChildCommandParsers(String parent) throws CommandNotFoundException {
        return reg.getChildCommandParsers(parent);
    }

    @Override
    public void addRegistrationListener(CommandRegistrationListener listener) {
        reg.addRegistrationListener(listener);
    }

    @Override
    public void removeRegistrationListener(CommandRegistrationListener listener) {
        reg.removeRegistrationListener(listener);
    }

    public String getCommandName(Command<CLICommandInvocation> command) throws CommandLineParserException {
        CommandContainer container = containerBuilder.create(command);
        return container.getParser().getProcessedCommand().name();
    }

    public List<String> getAvailableAeshCommands() {
        List<String> lst = new ArrayList<>();
        for (String c : getAllCommandNames()) {
            CommandLineParser cmdParser;
            try {
                cmdParser = findCommand(c, null);
            } catch (CommandNotFoundException ex) {
                continue;
            }
            CommandActivator activator = cmdParser.getProcessedCommand().getActivator();
            if (activator == null || activator.isActivated(new ParsedCommand(cmdParser.getProcessedCommand()))) {
                lst.add(c);
            }
        }
        return lst;
    }

    public boolean isLegacyCommand(String name) {
        try {
            CLICommandContainer c = (CLICommandContainer) getCommand(name, name);
            return c.getWrappedContainer() instanceof LegacyCommandContainer;
        } catch (CommandNotFoundException ex) {
            return false;
        }
    }
}
