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

import org.jboss.as.cli.impl.aesh.commands.operation.OperationCommandContainer;
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
import org.aesh.console.AeshContext;
import org.aesh.command.shell.Shell;
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
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.CommandExecutor;
import org.jboss.as.cli.impl.CommandExecutor.ExecutableBuilder;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.jboss.as.cli.impl.aesh.PluginsLoader.Loader;
import org.jboss.as.cli.impl.aesh.commands.LegacyCommandRewriters;
import org.jboss.as.cli.impl.aesh.commands.operation.LegacyCommandContainer.LegacyCommand;
import org.jboss.as.cli.impl.aesh.commands.operation.OperationCommandContainer.OperationCommand;
import org.jboss.as.cli.impl.aesh.commands.operation.SpecialCommand;
import org.jboss.modules.ModuleLoadException;
import org.wildfly.core.cli.command.BatchCompliantCommand;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * Aesh Commands.
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
    }

    public class CLIExecutor {

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

        public CommandHandler getLegacyHandler() {
            if (execution.getCommand() instanceof LegacyCommand) {
                return ((LegacyCommand) execution.getCommand()).getCommandHandler();
            }
            return null;
        }

        public boolean isOperation() {
            return execution.getCommand() instanceof OperationCommand;
        }

        public String getLine() {
            if (execution.getCommand() instanceof SpecialCommand) {
                return ((SpecialCommand) execution.getCommand()).getLine();
            }
            return null;
        }

    }

    private final CLICommandInvocationBuilder invocationBuilder;
    private final CommandRuntime<Command<CLICommandInvocation>, CLICommandInvocation> processor;
    private final CLICommandRegistry registry;
    private final CLICompletionHandler completionHandler;
    private final CommandContext ctx;

    private final Set<String> plugins = new HashSet<>();

    public AeshCommands(CommandContextImpl ctx, OperationCommandContainer op) throws CliInitializationException {
        this(ctx, null, op);
    }

    public AeshCommands(CommandContextImpl ctx, ReadlineConsole console, OperationCommandContainer op) throws CliInitializationException {
        this.ctx = ctx;
        registry = new CLICommandRegistry(ctx, op);
        Shell shell = null;
        if (console != null) {
            shell = new ReadlineShell(console, ctx);
        }
        invocationBuilder = new CLICommandInvocationBuilder(ctx, registry, console, shell);
        AeshCommandRuntimeBuilder builder = AeshCommandRuntimeBuilder.builder();
        processor = builder.
                commandRegistry(registry).
                // AND and OR operators can't be used.
                // The logic is based on execution of each command.
                // In the CLI, in batch mode we delay execution.
                // Furthermore && conflicts with operation name parsing.
                operators(EnumSet.of(OperatorType.REDIRECT_OUT,
                        OperatorType.PIPE, OperatorType.APPEND_OUT, OperatorType.REDIRECT_IN)).
                parseBrackets(true).
                aeshContext(new BridgedContext(ctx)).
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

    public CLIExecutor newExecutor(String line) throws CommandLineException, IOException, CommandNotFoundException {
        CLIExecutor exe;
        //Must rewrite some legacy commands with syntax not supported in aesh
        line = LegacyCommandRewriters.rewrite(line, ctx);

        try {
            exe = new CLIExecutor(processor.buildExecutor(line));
        } catch (CommandLineParserException | OptionValidatorException | CommandValidatorException ex) {
            throw new CommandLineException(ex.getLocalizedMessage());
        }
        return exe;
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


    /**
     * Load extensions.
     */
    public void loadPlugins(File path, String name, List<String> filter, Set<String> skip, Map<String, String> renaming) throws CommandLineException, ModuleLoadException, CommandLineParserException {
        Loader loader = PluginsLoader.newPluginsLoader();
        Iterable<Command> it = loader.loadPlugins(path, name, Command.class);
        addExtensions(it, filter, skip, renaming);
    }

    public Set<String> listPlugins(File path, String name) throws CommandLineException, ModuleLoadException, CommandLineParserException {
        Set<String> commands = new HashSet<>();
        Loader loader = PluginsLoader.newPluginsLoader();
        Iterable<Command> it = loader.loadPlugins(path, name, Command.class);
        for (Command command : it) {
            commands.add(getRegistry().getCommandName(command));
        }
        return commands;
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
