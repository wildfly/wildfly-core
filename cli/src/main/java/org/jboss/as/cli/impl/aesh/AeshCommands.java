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
import org.aesh.converter.CLConverterManager;
import org.aesh.io.FileResource;
import org.aesh.io.Resource;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CLICommandCompleter.Completer;
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
        for (Class<?> converted : CLConverterManager.getInstance().getConvertedTypes()) {
            CLConverterManager.getInstance().setConverter(converted,
                    new ExpressionValueConverter(CLConverterManager.getInstance().getConverter(converted)));
        }
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
                completerInvocationProvider(new CLICompleterInvocationProvider(ctx)).
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

    public void setLegacyCommandCompleter(Completer completer) {
        completionHandler.setLegacyCommandCompleter(completer);
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
