/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.extensions;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.util.HelpFormatter;
import org.wildfly.security.manager.WildFlySecurityManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Alexey Loubyansky
 */
public class CliExtCommandHandler extends CommandHandlerWithHelp {

    public static final String NAME = "test-cli-ext-commands";
    public static final String OUTPUT = "hello world!";

    public CliExtCommandHandler() {
        super(NAME, false);
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        ctx.printLine(OUTPUT);
    }

    @Override
    protected void printHelp(CommandContext ctx) throws CommandLineException {
        String filename = "help/" + NAME + ".txt";
        ClassLoader cl = WildFlySecurityManager.getClassLoaderPrivileged(CliExtCommandHandler.class);
        InputStream helpInput = cl.getResourceAsStream(filename);
        if (helpInput != null) {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(helpInput, StandardCharsets.UTF_8))) {
                HelpFormatter.format(ctx, reader);
            } catch (java.io.IOException e) {
                throw new CommandFormatException("Failed to read help/help.txt: " + e.getLocalizedMessage());
            }
        } else {
            throw new CommandFormatException("Failed to locate command description " + filename);
        }
    }

}
