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
}
