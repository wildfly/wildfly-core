/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.handlers.DefaultFilenameTabCompleter;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Alexey Loubyansky
 */
public class FilenameTabCompleterTestCase {

    private CommandContext ctx;
    private FileSystemPathArgument arg;
    private DefaultCallbackHandler parsedCmd;

    @Before
    public void setup() throws CliInitializationException {
        ctx = CommandContextFactory.getInstance().newCommandContext();
        final DefaultFilenameTabCompleter completer = new DefaultFilenameTabCompleter(ctx);

        final CommandHandlerWithArguments cmd = new CommandHandlerWithArguments() {

            @Override
            public boolean isAvailable(CommandContext ctx) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean isBatchMode(CommandContext ctx) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public void handle(CommandContext ctx) throws CommandLineException {
                // TODO Auto-generated method stub

            }
        };

        arg = new FileSystemPathArgument(cmd, completer, 0, "arg");
        parsedCmd = new DefaultCallbackHandler();
    }

    @After
    public void tearDown() {
        ctx.terminateSession();
        ctx = null;
        arg = null;
        parsedCmd = null;
    }

    @Test
    public void testTranslateGetValue() throws Exception {
        parsedCmd.parse(null, "cmd ~" + File.separator, ctx);
        assertEquals(SecurityActions.getProperty("user.home") + File.separator, arg.getValue(parsedCmd));
    }

    @Test
    public void testTranslateGetValueRequired() throws Exception {
        parsedCmd.parse(null, "cmd ~" + File.separator, ctx);
        assertEquals(SecurityActions.getProperty("user.home") + File.separator, arg.getValue(parsedCmd, true));
    }
}
