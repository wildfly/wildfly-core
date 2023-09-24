/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.cli;

import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.parser.CommandLineParser;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.aesh.AeshCommands;
import org.jboss.as.cli.impl.aesh.HelpSupport;
import org.junit.Test;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class PatchHelpTestCase {

    @Test
    public void testCLICommands() throws Exception {
        CommandContextImpl ctx = (CommandContextImpl) CommandContextFactory.getInstance().newCommandContext();
        AeshCommands commands = ctx.getAeshCommands();
        CommandContainer<CLICommandInvocation> container = commands.getRegistry().getCommand("patch", "patch");
        HelpSupport.checkCommand(null, container.getParser());
        for (CommandLineParser<CLICommandInvocation> child : container.getParser().getAllChildParsers()) {
            HelpSupport.checkCommand(container.getParser(), child);
        }
    }
}
