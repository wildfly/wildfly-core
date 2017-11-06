/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
import org.aesh.command.Command;
import org.aesh.command.CommandRuntime;
import org.aesh.command.shell.Shell;
import org.aesh.command.invocation.CommandInvocationBuilder;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.readline.Prompt;
import org.aesh.readline.terminal.Key;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.aesh.util.Parser;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class CLICommandInvocationBuilder implements
        CommandInvocationBuilder<Command<CLICommandInvocation>, CLICommandInvocation> {

    public class ShellImpl implements Shell {

        private final ReadlineConsole console;
        private final CommandContextImpl ctx;

        public ShellImpl(ReadlineConsole console, CommandContextImpl ctx) {
            this.console = console;
            this.ctx = ctx;
        }

        @Override
        public void write(String out) {
            ctx.print(out, false, false);
        }

        @Override
        public void write(char out) {
            ctx.print("" + out, false, false);
        }

        @Override
        public void writeln(String out) {
            ctx.print(out, true, false);
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
    public CLICommandInvocation build(CommandRuntime<Command<CLICommandInvocation>, CLICommandInvocation> runtime,
            CommandInvocationConfiguration configuration) {
        // We must set a CommandContext that can deal with timeout.
        // Another approach would have been to set the ctx CommandContext instance
        // and have the CommandExecutor to set the timeoutContext onto the CLICommandInvocation
        // but this would require a public setter on the CLICommandInvocationImpl class
        // something that we don't want.
        return new CLICommandInvocationImpl(ctx.newTimeoutCommandContext(),
                registry, console, shell, runtime, configuration);
    }

}
