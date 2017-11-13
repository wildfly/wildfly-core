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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aesh.command.Executor;
import org.aesh.readline.Prompt;
import org.aesh.readline.action.KeyAction;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.readline.AeshContext;
import org.aesh.command.shell.Shell;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandRuntime;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.command.parser.CommandLineParserException;
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
    CLICommandInvocationImpl(CommandContext ctx, CLICommandRegistry registry,
            ReadlineConsole console, Shell shell, CommandRuntime runtime,
            CommandInvocationConfiguration config) {
        this.ctx = ctx;
        this.registry = registry;
        this.console = console;
        this.shell = shell;
        this.runtime = runtime;
        this.config = config;
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
    public AeshContext getAeshContext() {
        return config.getAeshContext();
    }

    @Override
    public KeyAction input() throws InterruptedException {
        return shell.read();
    }

    @Override
    public String inputLine() throws InterruptedException {
        return inputLine(new Prompt(""));
    }

    @Override
    public String inputLine(Prompt prompt) throws InterruptedException {
        return shell.readLine();
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
        shell.write(msg);
    }

    @Override
    public void println(String msg) {
        shell.writeln(msg);
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
