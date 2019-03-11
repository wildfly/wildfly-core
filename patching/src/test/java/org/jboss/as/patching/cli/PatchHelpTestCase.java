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
