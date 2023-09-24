/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class OperatorsTestCase {

    @Test
    public void testPipe() throws CliInitializationException, CommandLineException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        try {
            ctx.handle("version | grep Rel");
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testRedirectOut() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        File f = File.createTempFile("cli_test" + System.currentTimeMillis(), null);
        f.delete();
        try {
            ctx.handle("version > " + f.getAbsolutePath());
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.isEmpty());
            List<String> lines = Files.readAllLines(f.toPath());
            boolean found = false;
            for (String s : lines) {
                if (s.contains("Release:")) {
                    found = true;
                }
            }
            if (!found) {
                throw new RuntimeException("Not right content " + lines);
            }
            consoleOutput.reset();
            ctx.handle("grep Rel " + f.getAbsolutePath());
            out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
        } finally {
            f.delete();
            ctx.terminateSession();
        }
    }

    @Test
    public void testPipeRedirectOut() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        File f = File.createTempFile("cli_test" + System.currentTimeMillis(), null);
        f.delete();
        try {
            ctx.handle("version | grep Rel > " + f.getAbsolutePath());
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.isEmpty());
            List<String> lines = Files.readAllLines(f.toPath());
            Assert.assertTrue(lines.toString(), lines.size() == 1);
            Assert.assertTrue(lines.toString(), lines.get(0).contains("Release:"));
            boolean found = false;
            for (String s : lines) {
                if (s.contains("Release:")) {
                    found = true;
                }
            }
            if (!found) {
                throw new RuntimeException("Not right content " + lines);
            }
        } finally {
            f.delete();
            ctx.terminateSession();
        }
    }

    @Test
    public void testAppendOut() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        File f = File.createTempFile("cli_test" + System.currentTimeMillis(), null);
        try {
            ctx.handle("version >> " + f.getAbsolutePath());
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.isEmpty());
            List<String> lines = Files.readAllLines(f.toPath());
            boolean found = false;
            for (String s : lines) {
                if (s.contains("Release:")) {
                    found = true;
                }
            }
            if (!found) {
                throw new RuntimeException("Not right content in " + f + ": " + lines);
            }
            consoleOutput.reset();
            ctx.handle("grep Rel " + f.getAbsolutePath());
            out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
        } finally {
            f.delete();
            ctx.terminateSession();
        }
    }

    @Test
    public void testAppendOutCreateFile() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        File useless = File.createTempFile("useless" + System.currentTimeMillis(), null);
        File actualFile = new File(useless.getParent(), "cli_test" + System.currentTimeMillis());
        try {
            ctx.handle("version >> " + actualFile.getAbsolutePath());
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.isEmpty());
            List<String> lines = Files.readAllLines(actualFile.toPath());
            boolean found = false;
            for (String s : lines) {
                if (s.contains("Release:")) {
                    found = true;
                }
            }
            if (!found) {
                throw new RuntimeException("Not right content in " + actualFile + ": " + lines);
            }
            consoleOutput.reset();
            ctx.handle("grep Rel " + actualFile.getAbsolutePath());
            out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
            ctx.handle("version >> " + actualFile.getAbsolutePath());
            List<String> lines2 = Files.readAllLines(actualFile.toPath());
            Assert.assertEquals(lines.size() * 2, lines2.size());
        } finally {
            useless.delete();
            actualFile.delete();
            ctx.terminateSession();
        }
    }

    @Test
    public void testVariables() throws CliInitializationException, CommandLineException, IOException {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        ctx.handle("set varName=Rel");
        try {
            ctx.handle("version | grep $varName");
            String out = consoleOutput.toString();
            Assert.assertTrue(out, out.contains("Release:"));
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testErrors() throws Exception {
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().setConsoleOutput(consoleOutput).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        try {
            ctx.handle("version ||");
            throw new Exception("Should have failed");
        } catch (CommandLineException ex) {
            Assert.assertTrue(ex.toString(), ex.getMessage().contains("aesh: syntax error near unexpected token '|'"));
        } finally {
            ctx.terminateSession();
        }
    }
}
