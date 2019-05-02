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

import java.util.List;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.parser.CommandLineCompletionParser;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.populator.CommandPopulator;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.readline.AeshContext;
import org.aesh.command.invocation.InvocationProviders;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.container.CommandContainerResult;
import org.aesh.command.container.DefaultCommandContainer;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.parser.OptionParserException;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.complete.AeshCompleteOperation;
import org.aesh.parser.ParsedLineIterator;
import org.aesh.parser.ParsedLine;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.operation.LegacyCommandContainer;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * All registered commands are Wrapped into instance of this class in order to
 * print help the CLI way.
 *
 * @author jfdenise
 */
public class CLICommandContainer extends DefaultCommandContainer<CLICommandInvocation> {

    private class WrappedParser implements CommandLineParser<CLICommandInvocation> {

        private final CommandLineParser<CLICommandInvocation> parser;

        private WrappedParser(CommandLineParser<CLICommandInvocation> parser) {
            this.parser = parser;
        }

        @Override
        public ProcessedCommand<Command<CLICommandInvocation>, CLICommandInvocation> getProcessedCommand() {
            return parser.getProcessedCommand();
        }

        @Override
        public Command<CLICommandInvocation> getCommand() {
            return parser.getCommand();
        }

        @Override
        public CommandLineCompletionParser getCompletionParser() {
            return parser.getCompletionParser();
        }

        @Override
        public List<String> getAllNames() {
            return parser.getAllNames();
        }

        @Override
        public CommandLineParser<CLICommandInvocation> getChildParser(String name) {
            return parser.getChildParser(name);
        }

        @Override
        public void addChildParser(CommandLineParser<CLICommandInvocation> childParser) throws CommandLineParserException {
            parser.addChildParser(childParser);
        }

        @Override
        public List<CommandLineParser<CLICommandInvocation>> getAllChildParsers() {
            return parser.getAllChildParsers();
        }

        @Override
        public CommandPopulator getCommandPopulator() {
            return parser.getCommandPopulator();
        }

        @Override
        public String printHelp() {
            return HelpSupport.getSubCommandHelp(CLICommandContainer.this.parser.getProcessedCommand().name(),
                    parser);
        }

        @Override
        public void parse(String line) {
            parser.parse(line);
        }

        @Override
        public void parse(String line, Mode mode) {
            parser.parse(line, mode);
        }

        @Override
        public void parse(ParsedLineIterator iterator, Mode mode) {
            parser.parse(iterator, mode);
        }

        @Override
        public void clear() {
            parser.clear();
        }

        @Override
        public boolean isGroupCommand() {
            return parser.isGroupCommand();
        }

        @Override
        public void setChild(boolean b) {
            parser.setChild(b);
        }

        @Override
        public void populateObject(String line, InvocationProviders invocationProviders,
                AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
            parser.populateObject(line, invocationProviders, aeshContext, mode);
        }

        @Override
        public ProcessedOption lastParsedOption() {
            return parser.lastParsedOption();
        }

        @Override
        public CommandLineParser<CLICommandInvocation> parsedCommand() {
            return parser.parsedCommand();
        }

        @Override
        public void complete(AeshCompleteOperation completeOperation, InvocationProviders invocationProviders) {
            parser.complete(completeOperation, invocationProviders);
        }

        @Override
        public void complete(AeshCompleteOperation completeOperation, ParsedLine line, InvocationProviders invocationProviders) {
            parser.complete(completeOperation, line, invocationProviders);
        }

        @Override
        public void doPopulate(ProcessedCommand processedCommand, InvocationProviders invocationProviders, AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
            parser.doPopulate(processedCommand, invocationProviders, aeshContext, mode);
        }

        @Override
        public void updateAnsiMode(boolean mode) {
            parser.updateAnsiMode(mode);
        }

        @Override
        public String getFormattedCommand(int offset, int descriptionStart) {
            return parser.getFormattedCommand(offset, descriptionStart);
        }
    }

    public class CLICommandParser implements CommandLineParser<CLICommandInvocation> {

        public CommandLineParser<CLICommandInvocation> getWrappedParser() {
            return container.getParser();
        }

        @Override
        public ProcessedCommand<Command<CLICommandInvocation>, CLICommandInvocation> getProcessedCommand() {
            return container.getParser().getProcessedCommand();
        }

        @Override
        public Command getCommand() {
            return container.getParser().getCommand();
        }

        @Override
        public CommandLineCompletionParser getCompletionParser() {
            return container.getParser().getCompletionParser();
        }

        @Override
        public List<String> getAllNames() {
            return container.getParser().getAllNames();
        }

        @Override
        public CommandLineParser<CLICommandInvocation> getChildParser(String name) {
            return container.getParser().getChildParser(name);
        }

        @Override
        public void addChildParser(CommandLineParser<CLICommandInvocation> childParser) throws CommandLineParserException {
            container.getParser().addChildParser(childParser);
        }

        @Override
        public List<CommandLineParser<CLICommandInvocation>> getAllChildParsers() {
            return container.getParser().getAllChildParsers();
        }

        @Override
        public CommandPopulator getCommandPopulator() {
            return container.getParser().getCommandPopulator();
        }

        @Override
        public String printHelp() {
            return doPrintHelp();
        }

        @Override
        public void parse(String line) {
            container.getParser().parse(line);
        }

        @Override
        public void parse(String line, Mode mode) {
            container.getParser().parse(line, mode);
        }

        @Override
        public void parse(ParsedLineIterator iterator, Mode mode) {
            container.getParser().parse(iterator, mode);
        }

        @Override
        public void clear() {
            container.getParser().clear();
        }

        @Override
        public boolean isGroupCommand() {
            return container.getParser().isGroupCommand();
        }

        @Override
        public void setChild(boolean b) {
            container.getParser().setChild(b);
        }

        @Override
        public void populateObject(String line, InvocationProviders invocationProviders, AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
            container.getParser().populateObject(line, invocationProviders, aeshContext, mode);
        }

        @Override
        public ProcessedOption lastParsedOption() {
            return container.getParser().lastParsedOption();
        }

        @Override
        public CommandLineParser<CLICommandInvocation> parsedCommand() {
            return container.getParser().parsedCommand();
        }

        @Override
        public void complete(AeshCompleteOperation completeOperation, InvocationProviders invocationProviders) {
            container.getParser().complete(completeOperation, invocationProviders);
        }

        @Override
        public void complete(AeshCompleteOperation completeOperation, ParsedLine line, InvocationProviders invocationProviders) {
            container.getParser().complete(completeOperation, line, invocationProviders);
        }

        @Override
        public void doPopulate(ProcessedCommand processedCommand, InvocationProviders invocationProviders, AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
            container.getParser().doPopulate(processedCommand, invocationProviders, aeshContext, mode);
        }

        @Override
        public void updateAnsiMode(boolean mode) {
            container.getParser().updateAnsiMode(mode);
        }

        @Override
        public String getFormattedCommand(int offset, int descriptionStart) {
            return container.getParser().getFormattedCommand(offset, descriptionStart);
        }
    }

    private final CommandContainer<CLICommandInvocation> container;
    private final CommandLineParser<CLICommandInvocation> parser;
    private final CommandContext ctx;

    CLICommandContainer(CommandContainer<CLICommandInvocation> container, CommandContext ctx) throws OptionParserException {
        this.container = container;
        this.ctx = ctx;
        this.parser = new CLICommandParser();
    }

    @Override
    public CommandLineParser<CLICommandInvocation> getParser() {
        return parser;
    }

    @Override
    public boolean haveBuildError() {
        return container.haveBuildError();
    }

    @Override
    public String getBuildErrorMessage() {
        return container.getBuildErrorMessage();
    }

    @Override
    public void close() throws Exception {
        container.close();
    }

    @Override
    public String printHelp(String childCommandName) {
        return doPrintHelp();
    }

    @Override
    public CommandContainerResult executeCommand(ParsedLine line,
            InvocationProviders invocationProviders,
            AeshContext aeshContext,
            CLICommandInvocation commandInvocation)
            throws CommandLineParserException, OptionValidatorException,
            CommandValidatorException, CommandException, InterruptedException {
        return container.executeCommand(line, invocationProviders, aeshContext, commandInvocation);
    }

    public CommandContainer getWrappedContainer() {
        return container;
    }

    public CommandLineParser<CLICommandInvocation> wrapParser(CommandLineParser<CLICommandInvocation> p) {
        return new WrappedParser(p);
    }

    private String doPrintHelp() {
        if (container.getParser() instanceof LegacyCommandContainer.CommandParser) {
            return container.getParser().printHelp();
        } else {
            return HelpSupport.getCommandHelp(parser);
        }
    }
}
