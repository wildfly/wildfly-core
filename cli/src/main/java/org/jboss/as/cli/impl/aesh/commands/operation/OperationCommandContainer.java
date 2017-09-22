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
package org.jboss.as.cli.impl.aesh.commands.operation;

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
 *
 * @author jdenise@redhat.com
 */
public class OperationCommandContainer extends DefaultCommandContainer<Command<CLICommandInvocation>, CLICommandInvocation> {

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

    public class OperationParser implements CommandLineParser<Command<CLICommandInvocation>> {

        @Override
        public ProcessedCommand<Command<CLICommandInvocation>> getProcessedCommand() {
            try {
                return new ProcessedCommandBuilder().command(command).name("/").create();
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
        public CommandLineParser<Command<CLICommandInvocation>> getChildParser(String name) {
            return null;
        }

        @Override
        public void addChildParser(CommandLineParser<Command<CLICommandInvocation>> childParser) {

        }

        @Override
        public List<CommandLineParser<Command<CLICommandInvocation>>> getAllChildParsers() {
            return Collections.emptyList();
        }

        @Override
        public CommandPopulator<Object, Command<CLICommandInvocation>> getCommandPopulator() {
            return new CommandPopulator<Object, Command<CLICommandInvocation>>() {
                @Override
                public void populateObject(ProcessedCommand<Command<CLICommandInvocation>> processedCommand,
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
        public CommandLineParser<Command<CLICommandInvocation>> parsedCommand() {
            return this;
        }

        @Override
        public void complete(AeshCompleteOperation co, InvocationProviders invocationProviders) {
            ctx.completeOperationAndLegacy(co);
        }

        @Override
        public void complete(AeshCompleteOperation completeOperation, ParsedLine line, InvocationProviders invocationProviders) {
            ctx.completeOperationAndLegacy(completeOperation);
        }

        @Override
        public void doPopulate(ProcessedCommand processedCommand, InvocationProviders invocationProviders, AeshContext aeshContext, Mode mode) throws CommandLineParserException, OptionValidatorException {
        }
    }

    private final Command<CLICommandInvocation> command = new OperationCommand();

    private String line;

    private final CommandLineParser<Command<CLICommandInvocation>> parser = new OperationParser();
    private final CommandContextImpl ctx;
    public OperationCommandContainer(CommandContextImpl ctx) {
        this.ctx = ctx;
    }

    @Override
    public CommandLineParser<Command<CLICommandInvocation>> getParser() {
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
