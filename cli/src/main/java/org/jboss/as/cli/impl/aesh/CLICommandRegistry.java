/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat Middleware LLC, and individual contributors
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
 *
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
import org.aesh.command.impl.parser.CommandLineParserBuilder;
import org.aesh.command.impl.registry.MutableCommandRegistryImpl;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.parser.OptionParserException;
import org.aesh.command.registry.MutableCommandRegistry;
import org.aesh.parser.ParsedLine;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.logging.Logger;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class CLICommandRegistry extends CommandRegistry implements org.aesh.command.registry.CommandRegistry {
    private static final Logger log = Logger.getLogger(CLICommandRegistry.class);
    private final MutableCommandRegistry reg = new MutableCommandRegistryImpl();
    private final AeshCommandContainerBuilder containerBuilder = new AeshCommandContainerBuilder();
    private final CommandContextImpl ctx;

    public CLICommandRegistry(CommandContextImpl ctx) {
        this.ctx = ctx;
    }

    @Override
    public CommandHandler remove(String cmdName) {
        removeCommand(cmdName);
        return super.remove(cmdName);
    }

    private CommandContainer addCommandContainer(CommandContainer container) throws CommandLineException {
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
        checkExistence(container.getParser().getProcessedCommand().name());
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

    public CommandContainer addCommand(Command<CLICommandInvocation> command) throws CommandLineException, CommandLineParserException {
        return addCommand(command, Collections.emptyMap());
    }

    public CommandContainer addCommand(Command<CLICommandInvocation> command, Map<String, String> renaming) throws CommandLineException, CommandLineParserException {
        CommandContainer container = containerBuilder.create(command);

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
                        ProcessedCommand cmd = container.getParser().getProcessedCommand();
                        // Add sub to existing command.
                        container = new AeshCommandContainer(
                                new CommandLineParserBuilder()
                                .processedCommand(new ProcessedCommandBuilder().
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
                                        create()).create());
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
            container = new AeshCommandContainer(
                    new CommandLineParserBuilder()
                    .processedCommand(new ProcessedCommandBuilder().
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
        return addCommand(container);
    }

    public CommandContainer addCommand(CommandContainer container) throws CommandLineException {
        return addCommandContainer(container);
    }

    @Override
    public CommandContainer<Command<CLICommandInvocation>, CLICommandInvocation> getCommand(String name, String line)
            throws CommandNotFoundException {
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
            CommandLineParser<? extends Command> cmdParser;
            try {
                cmdParser = findCommand(c, null);
            } catch (CommandNotFoundException ex) {
                continue;
            }
            CommandActivator activator = cmdParser.getProcessedCommand().getActivator();
            if (activator == null || activator.isActivated(cmdParser.getProcessedCommand())) {
                lst.add(c);
            }
        }
        return lst;
    }
}
