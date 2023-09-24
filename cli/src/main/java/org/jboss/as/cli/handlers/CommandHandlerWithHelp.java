/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.util.HelpFormatter;
import org.jboss.as.protocol.StreamUtils;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Abstract handler that checks whether the argument is '--help', in which case it
 * tries to locate file [cmd].txt and print its content. If the argument
 * is absent or isn't '--help', it'll call doHandle(ctx) method.
 *
 * @author Alexey Loubyansky
 */
public abstract class CommandHandlerWithHelp extends CommandHandlerWithArguments {

    private final String filename;
    private final boolean connectionRequired;
    protected ArgumentWithoutValue helpArg = new ArgumentWithoutValue(this, "--help", "-h");

    public CommandHandlerWithHelp(String command) {
        this(command, false);
    }

    public CommandHandlerWithHelp(String command, boolean connectionRequired) {
        checkNotNullParam("command", command);
        this.filename = "help/" + command + ".txt";
        this.connectionRequired = connectionRequired;
        this.helpArg.setExclusive(true);
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        if(connectionRequired && ctx.getModelControllerClient() == null) {
            return false;
        }
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandHandler#handle(org.jboss.as.cli.CommandContext)
     */
    @Override
    public void handle(CommandContext ctx) throws CommandLineException {

        recognizeArguments(ctx);

        if(helpArg.isPresent(ctx.getParsedCommandLine())) {
            printHelp(ctx);
            return;
        }

        if(!isAvailable(ctx)) {
            throw new CommandFormatException("The command is not available in the current context (e.g. required subsystems or connection to the controller might be unavailable).");
        }

        doHandle(ctx);
    }

    public void displayHelp(CommandContext ctx) throws CommandLineException {
        printHelp(ctx);
    }

    protected void printHelp(CommandContext ctx) throws CommandLineException {
        InputStream helpInput = WildFlySecurityManager.getClassLoaderPrivileged(CommandHandlerWithHelp.class).getResourceAsStream(filename);
        if(helpInput != null) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(helpInput, StandardCharsets.UTF_8));
/*                String helpLine = reader.readLine();
                while(helpLine != null) {
                    ctx.printLine(helpLine);
                    helpLine = reader.readLine();
                }
*/
                HelpFormatter.format(ctx, reader);
            } catch(java.io.IOException e) {
                throw new CommandFormatException ("Failed to read help/help.txt: " + e.getLocalizedMessage());
            } finally {
                StreamUtils.safeClose(reader);
            }
        } else {
            throw new CommandFormatException("Failed to locate command description " + filename);
        }
    }

    protected abstract void doHandle(CommandContext ctx) throws CommandLineException;

    @Override
    public boolean isBatchMode(CommandContext ctx) {
        return false;
    }

    /**
     * Prints a list of strings. If -l switch is present then the list is printed one item per line,
     * otherwise the list is printed in columns.
     * @param ctx  the context
     * @param list  the list to print
     */
    protected void printList(CommandContext ctx, Collection<String> list, boolean l) {
        if(l) {
            for(String item : list) {
                ctx.printLine(item);
            }
        } else {
            ctx.printColumns(list);
        }
    }
}
