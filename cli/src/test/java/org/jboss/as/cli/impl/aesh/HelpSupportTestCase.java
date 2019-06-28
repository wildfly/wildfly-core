/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh;

import org.aesh.command.Command;
import org.aesh.command.impl.container.AeshCommandContainerBuilder;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.terminal.utils.Config;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.impl.CommandContextImpl;
import static org.jboss.as.cli.impl.aesh.AeshCommands.isAeshExtension;
import org.jboss.as.cli.impl.aesh.cmd.operation.LegacyCommandContainer;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class HelpSupportTestCase {

    private static final AeshCommandContainerBuilder containerBuilder = new AeshCommandContainerBuilder();

    @Test
    public void testStandalone() throws Exception {
        for (Class<? extends Command> clazz : Commands.TESTS_STANDALONE) {
            testStandalone(clazz);
        }
    }

    @Test
    public void testStandaloneOnly() throws Exception {
        for (Class<? extends Command> clazz : Commands.TESTS_STANDALONE_ONLY) {
            testStandaloneOnly(clazz);
        }
    }

    @Test
    public void testDomain() throws Exception {
        for (Class<? extends Command> clazz : Commands.TESTS_DOMAIN) {
            testDomain(clazz);
        }
    }

    @Test
    public void testCLICommands() throws Exception {
        CommandContextImpl ctx = (CommandContextImpl) CommandContextFactory.getInstance().newCommandContext();
        AeshCommands commands = ctx.getAeshCommands();
        for (String name : commands.getRegistry().getAllCommandNames()) {
            CLICommandContainer container = (CLICommandContainer) commands.getRegistry().getCommand(name, name);
            if (!(container.getWrappedContainer() instanceof LegacyCommandContainer)
                    && !isAeshExtension(container.getParser().getCommand())) {
                HelpSupport.checkCommand(null, container.getParser());
                for (CommandLineParser<CLICommandInvocation> child : container.getParser().getAllChildParsers()) {
                    HelpSupport.checkCommand(container.getParser(), child);
                }
            }
        }
    }

    @Test
    public void testInvalidCommand() throws Exception {
        boolean failed = false;
        try {
            HelpSupport.checkCommand(null,
                    new AeshCommandContainerBuilder().create(Commands.Standalone.Command1.class).getParser());
        } catch (Exception ex) {
            failed = true;
        }
        if (!failed) {
            throw new Exception("Check should have failed");
        }
    }

    private static void testStandalone(Class<? extends Command> clazz) throws Exception {
        Command c = clazz.newInstance();
        String synopsis = getStandaloneSynopsis(c);
        Assert.assertEquals(clazz.getName() + ". EXPECTED [" + ((TestCommand) c).getSynopsis()
                + "]. FOUND [" + synopsis + "]", ((TestCommand) c).getSynopsis(), synopsis);
    }

    private static void testStandaloneOnly(Class<? extends Command> clazz) throws Exception {
        Command c = clazz.newInstance();
        String synopsis = getStandaloneOnlySynopsis(c);
        Assert.assertEquals(clazz.getName() + ". EXPECTED [" + ((TestCommand) c).getSynopsis()
                + "]. FOUND [" + synopsis + "]", ((TestCommand) c).getSynopsis(), synopsis);
    }

    private static void testDomain(Class<? extends Command> clazz) throws Exception {
        Command c = clazz.newInstance();
        String synopsis = getDomainSynopsis(c);
        Assert.assertEquals(clazz.getName() + ". EXPECTED [" + ((TestCommand) c).getSynopsis()
                + "]. FOUND [" + synopsis + "]", ((TestCommand) c).getSynopsis(), synopsis);
    }

    private static String getStandaloneSynopsis(Command c) throws CommandLineParserException {
        return getSynopsis(c, "SYNOPSIS", "DESCRIPTION");
    }

    private static String getStandaloneOnlySynopsis(Command c) throws CommandLineParserException {
        return getSynopsis(c, "Standalone mode:", "Domain mode:");
    }

    private static String getDomainSynopsis(Command c) throws CommandLineParserException {
        return getSynopsis(c, "Domain mode:", "DESCRIPTION");
    }

    private static String getSynopsis(Command<CLICommandInvocation> c, String markerStart, String markerEnd) throws CommandLineParserException {
        String fullHelp = HelpSupport.getCommandHelp(containerBuilder.create(c).getParser());
        int begin = fullHelp.indexOf(markerStart);
        int start = begin + (markerStart + Config.getLineSeparator() + Config.getLineSeparator()).length();
        int end = fullHelp.indexOf(Config.getLineSeparator() + Config.getLineSeparator() + markerEnd);
        String synopsis = fullHelp.substring(start, end).trim();
        String[] lines = synopsis.split("\\n");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(" " + line.trim());
        }
        return builder.toString().trim();
    }

}
