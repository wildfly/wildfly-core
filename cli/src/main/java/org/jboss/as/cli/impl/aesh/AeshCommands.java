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
package org.jboss.as.cli.impl.aesh;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.command.Executor;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.readline.AeshContext;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandRuntime;
import org.aesh.command.Execution;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.operator.OperatorType;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.io.FileResource;
import org.aesh.io.Resource;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.CommandExecutor;
import org.jboss.as.cli.impl.CommandExecutor.ExecutableBuilder;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.wildfly.core.cli.command.BatchCompliantCommand;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * This class is the entry point to interact with the Aesh Command Runtime.
 *
 * @author jdenise@redhat.com
 */
public class AeshCommands {

    private class BridgedContext implements AeshContext {

        private final CommandContext ctx;

        private BridgedContext(CommandContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public Resource getCurrentWorkingDirectory() {
            return new FileResource(ctx.getCurrentDir().getAbsoluteFile());
        }

        @Override
        public void setCurrentWorkingDirectory(Resource cwd) {
            ctx.setCurrentDir(new File(cwd.getAbsolutePath()));
        }

        @Override
        public Set<String> exportedVariableNames() {
            return new HashSet<>(ctx.getVariables());
        }

        @Override
        public String exportedVariable(String key) {
            return ctx.getVariable(key);
        }
    }

    private class CLIExecutor {

        private final List<CLIExecution> executions;

        CLIExecutor(Executor<CLICommandInvocation> executor) {
            List<CLIExecution> lst = new ArrayList<>();
            for (Execution<CLICommandInvocation> ex : executor.getExecutions()) {
                lst.add(new CLIExecution(ex));
            }
            executions = Collections.unmodifiableList(lst);
        }

        public List<CLIExecution> getExecutions() {
            return executions;
        }
    }

    public class CLIExecution {

        private final Execution<CLICommandInvocation> execution;

        CLIExecution(Execution<CLICommandInvocation> execution) {
            this.execution = execution;
        }

        public CLICommandInvocation getInvocation() {
            return execution.getCommandInvocation();
        }

        public BatchCompliantCommand getBatchCompliant() {
            if (execution.getCommand() instanceof BatchCompliantCommand) {
                return (BatchCompliantCommand) execution.getCommand();
            }
            return null;
        }

        public DMRCommand getDMRCompliant() {
            if (execution.getCommand() instanceof DMRCommand) {
                return (DMRCommand) execution.getCommand();
            }
            return null;
        }

    }

    private final CLICommandInvocationBuilder invocationBuilder;
    private final CommandRuntime<Command<CLICommandInvocation>, CLICommandInvocation> processor;
    private final CLICommandRegistry registry;
    private final CLICompletionHandler completionHandler;

    private final Set<String> plugins = new HashSet<>();

    public AeshCommands(CommandContextImpl ctx) throws CliInitializationException {
        this(ctx, null);
    }

    public AeshCommands(CommandContextImpl ctx, ReadlineConsole console) throws CliInitializationException {
        registry = new CLICommandRegistry(ctx);
        invocationBuilder = new CLICommandInvocationBuilder(ctx, registry, console);
        AeshCommandRuntimeBuilder builder = AeshCommandRuntimeBuilder.builder();
        processor = builder.
                commandRegistry(registry).
                parseBrackets(true).
                aeshContext(new BridgedContext(ctx)).
                // for now only support '>' as it is already supported for
                // commands/operation handlers
                operators(EnumSet.of(OperatorType.REDIRECT_OUT)).
                commandActivatorProvider(new CLICommandActivatorProvider(ctx)).
                commandInvocationBuilder(invocationBuilder).
                completerInvocationProvider(new CLICompleterInvocationProvider(ctx, registry)).
                converterInvocationProvider(new CLIConverterInvocationProvider(ctx)).
                optionActivatorProvider(new CLIOptionActivatorProvider(ctx)).
                validatorInvocationProvider(new CLIValidatorInvocationProvider(ctx)).
                build();
        completionHandler = new CLICompletionHandler(this, ctx);
        if (console != null) {
            console.setCompletionHandler(completionHandler);
            console.addCompleter(completionHandler);
        }
    }

    public CommandLineCompleter getCommandCompleter() {
        return completionHandler;
    }

    public CLICommandRegistry getRegistry() {
        return registry;
    }

    AeshContext getAeshContext() {
        return processor.getAeshContext();
    }

    public void complete(AeshCompleteOperation co) {
        processor.complete(co);
    }

    private CLIExecutor newExecutor(String line) throws CommandFormatException, IOException, CommandNotFoundException {
        CLIExecutor exe;

        try {
            exe = new CLIExecutor(processor.buildExecutor(line));
        } catch (CommandLineParserException | OptionValidatorException | CommandValidatorException ex) {
            throw new CommandFormatException(ex.getLocalizedMessage());
        }
        return exe;
    }

    public CLIExecution newExecution(ParsedCommandLine line) throws CommandFormatException, IOException, CommandNotFoundException {
        CLIExecutor exe = newExecutor(line.getSubstitutedLine());
        if (exe.getExecutions().isEmpty()) {
            throw new CommandFormatException("Invalid command " + line.getOriginalLine());
        }
        // No support for multiple operations on the same line yet.
        return exe.getExecutions().get(0);
    }

    public ExecutableBuilder newExecutableBuilder(CLIExecution exe) {
        return new ExecutableBuilder() {
            @Override
            public CommandExecutor.Executable build() {
                return () -> {
                    try {
                        exe.execution.execute();
                    } catch (CommandException | CommandValidatorException ex) {
                        throw new CommandLineException(ex.getLocalizedMessage());
                    } catch (InterruptedException ex) {
                        Thread.interrupted();
                        throw new CommandLineException(ex);
                    }
                };
            }

            @Override
            public CommandContext getCommandContext() {
                return exe.getInvocation().getCommandContext();
            }
        };
    }

    public void registerExtraCommands() throws CommandLineException, CommandLineParserException {
        ServiceLoader<Command> loader = ServiceLoader.load(Command.class);
        addExtensions(loader, null);
    }

    private void addExtensions(Iterable<Command> loader, List<String> filter) throws CommandLineException, CommandLineParserException {
        addExtensions(loader, filter, Collections.emptySet(), Collections.emptyMap());
    }

    private void addExtensions(Iterable<Command> loader, List<String> filter, Set<String> skip, Map<String, String> renaming) throws CommandLineException, CommandLineParserException {
        for (Command command : loader) {
            if (filter != null && !filter.isEmpty()) {
                String name = getRegistry().getCommandName(command);
                if (!filter.contains(name) || skip.contains(name)) {
                    continue;
                }
            }
            CommandContainer c = getRegistry().addCommand(command, renaming);
            plugins.add(c.getParser().getProcessedCommand().name());
        }
    }

    public Set<String> getPlugins() {
        return new TreeSet<>(plugins);
    }
}
