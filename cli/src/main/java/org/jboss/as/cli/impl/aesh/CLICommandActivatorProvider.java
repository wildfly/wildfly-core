/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import org.wildfly.core.cli.command.aesh.activator.CLICommandActivator;
import org.aesh.command.activator.CommandActivator;
import org.aesh.command.activator.CommandActivatorProvider;
import org.jboss.as.cli.CommandContext;

/**
 * A CLI specific {@code CommandActivatorProvider} used to set
 * {@link org.jboss.as.cli.CommandContext} on {@code CLICommandActivator}
 * instance.
 *
 * @author jdenise@redhat.com
 */
public class CLICommandActivatorProvider implements CommandActivatorProvider {

    private final CommandContext commandContext;

    public CLICommandActivatorProvider(CommandContext commandContext) {
        this.commandContext = commandContext;
    }

    @Override
    public CommandActivator enhanceCommandActivator(CommandActivator commandActivator) {

        if (commandActivator instanceof CLICommandActivator) {
            ((CLICommandActivator) commandActivator).setCommandContext(commandContext);
        }

        return commandActivator;
    }
}
