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
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class CLICommandInvocationBuilder implements
        CommandInvocationBuilder<Command<CLICommandInvocation>, CLICommandInvocation> {

    private final ReadlineConsole console;
    private final CommandContextImpl ctx;
    private final CLICommandRegistry registry;
    private final Shell shell;

    CLICommandInvocationBuilder(CommandContextImpl ctx,
            CLICommandRegistry registry, ReadlineConsole console, Shell shell) {
        this.ctx = ctx;
        this.registry = registry;
        this.console = console;
        this.shell = shell;
    }

    @Override
    public CLICommandInvocation build(CommandRuntime<Command<CLICommandInvocation>, CLICommandInvocation> runtime,
            CommandInvocationConfiguration configuration) {
        return new CLICommandInvocationImpl(ctx.getContextualCommandContext(),
                registry, console, shell, runtime, configuration);
    }

}
