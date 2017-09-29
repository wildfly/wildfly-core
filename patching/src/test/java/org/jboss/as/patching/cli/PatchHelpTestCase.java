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
package org.jboss.as.patching.cli;

import org.aesh.command.Command;
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
        CommandContainer<Command<CLICommandInvocation>, CLICommandInvocation> container = commands.getRegistry().getCommand("patch", "patch");
        HelpSupport.checkCommand(null, container.getParser());
        for (CommandLineParser<Command<CLICommandInvocation>> child : container.getParser().getAllChildParsers()) {
            HelpSupport.checkCommand(container.getParser(), child);
        }
    }
}
