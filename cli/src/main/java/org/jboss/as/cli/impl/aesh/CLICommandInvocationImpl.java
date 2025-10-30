/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aesh.command.Executor;
import org.aesh.readline.Prompt;
import org.aesh.readline.action.KeyAction;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.command.shell.Shell;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandRuntime;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.terminal.Key;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * The concrete {@code CLICommandInvocation}.
 *
 * @author jdenise@redhat.com
 */
class CLICommandInvocationImpl implements CLICommandInvocation {

    private final CommandContext ctx;
    private final CLICommandRegistry registry;
    private final Shell shell;
    private final ReadlineConsole console;
    private final CommandInvocationConfiguration config;
    private final CommandRuntime runtime;
    private final CommandContainer<CLICommandInvocation> commandContainer;
    CLICommandInvocationImpl(CommandContext ctx, CLICommandRegistry registry,
            ReadlineConsole console, Shell shell, CommandRuntime runtime,
            CommandInvocationConfiguration config, CommandContainer<CLICommandInvocation> commandContainer) {
        this.ctx = ctx;
        this.registry = registry;
        this.console = console;
        this.shell = shell;
        this.runtime = runtime;
        this.config = config;
        this.commandContainer = commandContainer;
    }

    @Override
    public CommandContext getCommandContext() {
        return ctx;
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
    public KeyAction input() throws InterruptedException {
        return shell.read();
    }

    @Override
    public Key input(long timeout, TimeUnit unit) throws InterruptedException {
        return shell.read(timeout, unit);
    }

    @Override
    public String inputLine() throws InterruptedException {
        return inputLine(new Prompt(""));
    }

    @Override
    public String inputLine(Prompt prompt) throws InterruptedException {
        return shell.readLine(prompt);
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
    public void print(String msg, boolean paging) {
        shell.write(msg, paging);
    }

    @Override
    public void println(String msg, boolean paging) {
        shell.writeln(msg, paging);
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

    @Override
    public String inputLine(Prompt prompt, Completion completer) throws InterruptedException, IOException {
        if (console != null) {
            return console.readLine(prompt, completer);
        }
        return null;
    }

    @Override
    public String getHelpInfo() {
        return commandContainer.getParser().parsedCommand().printHelp();
    }
}
