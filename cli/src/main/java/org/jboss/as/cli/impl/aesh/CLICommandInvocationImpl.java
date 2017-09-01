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

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aesh.command.Executor;
import org.aesh.readline.Prompt;
import org.aesh.readline.action.KeyAction;
import org.aesh.util.Parser;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.console.AeshContext;
import org.aesh.command.shell.Shell;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandRuntime;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.readline.terminal.Key;
import org.aesh.terminal.tty.Size;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
class CLICommandInvocationImpl implements CLICommandInvocation {

    // XXX jfdenise, we could be ridoff this class and use ReadlineShell in
    // all cases.
    private class NonInteractiveShell implements Shell {

        @Override
        public void write(String out) {
            ctx.print(out);
        }

        @Override
        public void writeln(String out) {
            ctx.printLine(out);
        }

        @Override
        public void write(int[] out) {
            ctx.print(Parser.stripAwayAnsiCodes(Parser.fromCodePoints(out)));
        }

        @Override
        public void write(char c) {
            ctx.print(Parser.stripAwayAnsiCodes(Parser.fromCodePoints(new int[]{c})));
        }

        @Override
        public String readLine() throws InterruptedException {
            return readLine(new Prompt(""));
        }

        @Override
        public String readLine(Prompt prompt) throws InterruptedException {
            try {
                return ctx.readLine(Parser.stripAwayAnsiCodes(Parser.
                        fromCodePoints(prompt.getPromptAsString())), prompt.isMasking());
            } catch (CommandLineException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Key read() throws InterruptedException {
            return null;
        }

        @Override
        public Key read(Prompt prompt) throws InterruptedException {
            return null;
        }

        @Override
        public boolean enableAlternateBuffer() {
            return false;
        }

        @Override
        public boolean enableMainBuffer() {
            return false;
        }

        @Override
        public Size size() {
            return new Size(80, 40);
        }

        @Override
        public void clear() {
        }
    }

    private final CommandContext ctx;
    private final CLICommandRegistry registry;
    private final Shell shell;
    private final ReadlineConsole console;
    private final CommandInvocationConfiguration config;
    private final CommandRuntime runtime;
    CLICommandInvocationImpl(CommandContext ctx, CLICommandRegistry registry,
            ReadlineConsole console, Shell shell, CommandRuntime runtime,
            CommandInvocationConfiguration config) {
        this.ctx = ctx;
        this.registry = registry;
        this.console = console;
        this.shell = shell != null ? shell : new NonInteractiveShell();
        this.runtime = runtime;
        this.config = config;
    }

    @Override
    public CommandContext getCommandContext() {
        return ctx;
    }

    @Override
    public CLICommandRegistry getCommandRegistry() {
        return registry;
    }

    @Override
    public Shell getShell() {
        return shell;
    }

    @Override
    public void setPrompt(Prompt prompt) {
        if (console != null) {
            console.setPrompt(prompt);
        }
    }

    @Override
    public Prompt getPrompt() {
        if (console != null) {
            return console.getPrompt();
        }
        return null;
    }

    @Override
    public String getHelpInfo(String commandName) {
        try {
            // This parser knows how to print help content.
            CommandLineParser parser = registry.findCommand(commandName, commandName);
            if (parser != null) {
                return parser.printHelp();
            }
        } catch (CommandNotFoundException ex) {
            Logger.getLogger(CLICommandInvocationImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    @Override
    public void stop() {
        ctx.terminateSession();
    }

    @Override
    public AeshContext getAeshContext() {
        return config.getAeshContext();
    }

    @Override
    public KeyAction input() throws InterruptedException {
        try {
            return Key.findStartKey(ctx.input());
        } catch (CommandLineException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String inputLine() throws InterruptedException {
        return inputLine(new Prompt(""));
    }

    @Override
    public String inputLine(Prompt prompt) throws InterruptedException {
        try {
            return ctx.input(Parser.stripAwayAnsiCodes(Parser.
                    fromCodePoints(prompt.getPromptAsString())), prompt.isMasking());
        } catch (CommandLineException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void executeCommand(String input) throws
            CommandNotFoundException,
            CommandLineParserException,
            OptionValidatorException,
            CommandValidatorException,
            CommandException, InterruptedException, IOException {
        runtime.executeCommand(input);
    }

    @Override
    public void print(String msg) {
        ctx.print(msg);
    }

    @Override
    public void println(String msg) {
        ctx.println(msg);
    }

    public void printColumns(Collection<String> col) {
        ctx.printColumns(col);
    }

    @Override
    public Executor<? extends CommandInvocation> buildExecutor(String line) throws CommandNotFoundException,
            CommandLineParserException,
            OptionValidatorException,
            CommandValidatorException,
            IOException {
        return runtime.buildExecutor(line);
    }

    @Override
    public CommandInvocationConfiguration getConfiguration() {
        return config;
    }

}
