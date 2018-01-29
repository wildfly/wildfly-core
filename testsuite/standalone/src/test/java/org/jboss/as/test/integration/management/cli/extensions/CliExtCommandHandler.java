/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
