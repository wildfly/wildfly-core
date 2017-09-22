/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.as.cli.impl.aesh;

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
    private class ReadlineShell implements Shell {

        private final ReadlineConsole console;
        private final CommandContextImpl ctx;

        public ReadlineShell(ReadlineConsole console, CommandContextImpl ctx) {
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
            } catch (CommandLineException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public String readLine(Prompt prompt) throws InterruptedException {
            try {
                return ctx.input(prompt);
            } catch (CommandLineException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Key read() throws InterruptedException {
            try {
                return Key.findStartKey(ctx.input());
            } catch (CommandLineException ex) {
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
            return console.getConnection().put(Capability.enter_ca_mode);
        }

        @Override
        public boolean enableMainBuffer() {
            return console.getConnection().put(Capability.exit_ca_mode);
        }

        @Override
        public Size size() {
            return console.getConnection().size();
        }

        @Override
        public void clear() {
            console.clearScreen();
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
        this.shell = new ReadlineShell(console, ctx);
    }

    @Override
    public CLICommandInvocation build(CommandRuntime<Command<CLICommandInvocation>, CLICommandInvocation> runtime,
            CommandInvocationConfiguration configuration) {
        return new CLICommandInvocationImpl(ctx.getContextualCommandContext(),
                registry, console, shell, runtime, configuration);
    }

}
