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
package org.jboss.as.cli.impl.aesh.cmd.operation;

import java.util.Collections;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.container.DefaultCommandContainer;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedCommandBuilder;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.impl.parser.CommandLineCompletionParser;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.invocation.InvocationProviders;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.populator.CommandPopulator;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.parser.ParsedLine;
import org.aesh.parser.ParsedLineIterator;
import org.aesh.readline.AeshContext;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * A container that handles DMR operations in order to allow to mix legacy
 * commands with new commands when aesh operators are in use. Aesh parsing and
 * completion are disabled and delegated to legacy parsing and completion.
 * Because the set of operation names is unbounded, any command that starts with
 * a ":", "/" or "." is concidered an operation and this container will handle
 * the command.
 *
 * @author jdenise@redhat.com
 */
public class OperationCommandContainer extends DefaultCommandContainer<CLICommandInvocation> {

    public static boolean isOperation(String mainCommand) {
        mainCommand = mainCommand.trim();
        return mainCommand.startsWith(":") || mainCommand.startsWith(".") || mainCommand.startsWith("/");
    }

    public class OperationCommand implements Command<CLICommandInvocation>, SpecialCommand {

        @Override
        public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
            throw new CommandException("Should never be called directly.");
        }

        @Override
        public String getLine() {
            return line;
        }
    }

    public class OperationParser implements CommandLineParser<CLICommandInvocation> {

        @Override
        public ProcessedCommand<Command<CLICommandInvocation>, CLICommandInvocation> getProcessedCommand() {
            try {
                return ProcessedCommandBuilder.<Command<CLICommandInvocation>, CLICommandInvocation>builder().command(command).name("/").create();
            } catch (CommandLineParserException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Command getCommand() {
            return command;
        }

        @Override
        public CommandLineCompletionParser getCompletionParser() {
            return null;
        }

        @Override
        public List<String> getAllNames() {
            return Collections.emptyList();
        }

        @Override
        public CommandLineParser<CLICommandInvocation> getChildParser(String name) {
            return null;
        }

        @Override
        public void addChildParser(CommandLineParser<CLICommandInvocation> childParser) {

        }

        @Override
        public List<CommandLineParser<CLICommandInvocation>> getAllChildParsers() {
            return Collections.emptyList();
        }

        @Override
        public CommandPopulator<Object, CLICommandInvocation> getCommandPopulator() {
            return new CommandPopulator<Object, CLICommandInvocation>() {
                @Override
                public void populateObject(ProcessedCommand<Command<CLICommandInvocation>, CLICommandInvocation> processedCommand,
                        InvocationProviders invocationProviders, AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
                }

                @Override
                public Object getObject() {
                    return command;
                }
            };
        }

        @Override
        public void populateObject(String line, InvocationProviders invocationProviders,
                AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {

        }

        @Override
        public String printHelp() {
            return null;
        }

        @Override
        public void parse(String line) {

        }

        @Override
        public ProcessedOption lastParsedOption() {
            return null;
        }

        @Override
        public void parse(String line, Mode mode) {
            OperationCommandContainer.this.line = line;
        }

        @Override
        public void parse(ParsedLineIterator iterator, Mode mode) {
            line = iterator.originalLine();
        }

        @Override
        public void clear() {

        }

        @Override
        public boolean isGroupCommand() {
            return false;
        }

        @Override
        public void setChild(boolean b) {
        }

        @Override
        public CommandLineParser<CLICommandInvocation> parsedCommand() {
            return this;
        }

        @Override
        public void complete(AeshCompleteOperation co, InvocationProviders invocationProviders) {
            ctx.getLegacyCommandCompleter().complete(ctx, null, co);
        }

        @Override
        public void complete(AeshCompleteOperation completeOperation, ParsedLine line, InvocationProviders invocationProviders) {
            ctx.getLegacyCommandCompleter().complete(ctx, null, completeOperation);
        }

        @Override
        public void doPopulate(ProcessedCommand processedCommand, InvocationProviders invocationProviders, AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
        }

        @Override
        public void updateAnsiMode(boolean mode) {

        }

        @Override
        public String getFormattedCommand(int offset, int descriptionStart) {
            return null;
        }
    }

    private final Command<CLICommandInvocation> command = new OperationCommand();

    private String line;

    private final CommandLineParser<CLICommandInvocation> parser = new OperationParser();
    private final CommandContextImpl ctx;
    public OperationCommandContainer(CommandContextImpl ctx) {
        this.ctx = ctx;
    }

    @Override
    public CommandLineParser<CLICommandInvocation> getParser() {
        return parser;
    }

    @Override
    public boolean haveBuildError() {
        return false;
    }

    @Override
    public String getBuildErrorMessage() {
        return null;
    }

    @Override
    public void close() throws Exception {
    }
}
