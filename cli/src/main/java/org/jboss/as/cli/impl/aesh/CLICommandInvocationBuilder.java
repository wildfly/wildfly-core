/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import java.io.IOException;
import org.aesh.command.CommandRuntime;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.shell.Shell;
import org.aesh.command.invocation.CommandInvocationBuilder;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.readline.Prompt;
import org.aesh.readline.terminal.Key;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.aesh.readline.util.Parser;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * A builder for {@link org.wildfly.core.cli.command.aesh.CLICommandInvocation}.
 * {@link org.wildfly.core.cli.command.aesh.CLICommandInvocation} is the bridge
 * between the CLI console and the commands.
 *
 * @author jdenise@redhat.com
 */
public class CLICommandInvocationBuilder implements
        CommandInvocationBuilder<CLICommandInvocation> {

    public class ShellImpl implements Shell {

        private final ReadlineConsole console;
        private final CommandContextImpl ctx;

        public ShellImpl(ReadlineConsole console, CommandContextImpl ctx) {
            this.console = console;
            this.ctx = ctx;
        }

        @Override
        public void write(String out, boolean paging) {
            ctx.print(out, false, paging);
        }

        @Override
        public void write(char out) {
            ctx.print("" + out, false, false);
        }

        @Override
        public void writeln(String out, boolean paging) {
            ctx.print(out, true, paging);
        }

        @Override
        public void write(int[] out) {
            ctx.print(Parser.stripAwayAnsiCodes(Parser.fromCodePoints(out)), false, false);
        }

        @Override
        public String readLine() throws InterruptedException {
            try {
                return ctx.input("", false);
            } catch (CommandLineException | IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public String readLine(Prompt prompt) throws InterruptedException {
            try {
                return ctx.input(prompt);
            } catch (CommandLineException | IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Key read() throws InterruptedException {
            try {
                return Key.findStartKey(ctx.input());
            } catch (CommandLineException | IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Key read(Prompt prompt) throws InterruptedException {
            //TODO
            return null;
        }

        @Override
        public boolean enableAlternateBuffer() {
            return console == null ? false : console.getConnection().put(Capability.enter_ca_mode);
        }

        @Override
        public boolean enableMainBuffer() {
            return console == null ? false : console.getConnection().put(Capability.exit_ca_mode);
        }

        @Override
        public Size size() {
            return console == null ? new Size(80, 40) : console.getConnection().size();
        }

        @Override
        public void clear() {
            if (console != null) {
                console.clearScreen();
            }
        }
    }

    private final ReadlineConsole console;
    private final CommandContextImpl ctx;
    private final CLICommandRegistry registry;
    private final Shell shell;

    CLICommandInvocationBuilder(CommandContextImpl ctx,
            CLICommandRegistry registry, ReadlineConsole console) {
        this.ctx = ctx;
        this.registry = registry;
        this.console = console;
        this.shell = new ShellImpl(console, ctx);
    }

    @Override
    public CLICommandInvocation build(CommandRuntime<CLICommandInvocation> runtime,
            CommandInvocationConfiguration configuration, CommandContainer<CLICommandInvocation> commandContainer) {
        // We must set a CommandContext that can deal with timeout.
        // Another approach would have been to set the ctx CommandContext instance
        // and have the CommandExecutor to set the timeoutContext onto the CLICommandInvocation
        // but this would require a public setter on the CLICommandInvocationImpl class
        // something that we don't want.
        return new CLICommandInvocationImpl(ctx.newTimeoutCommandContext(),
                registry, console, shell, runtime, configuration, commandContainer);
    }

}
