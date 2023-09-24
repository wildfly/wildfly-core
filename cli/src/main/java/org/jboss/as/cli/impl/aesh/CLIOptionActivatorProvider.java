/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import org.wildfly.core.cli.command.aesh.activator.CLIOptionActivator;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.activator.OptionActivatorProvider;
import org.jboss.as.cli.impl.CommandContextImpl;

/**
 * A CLI specific {@code OptionActivatorProvider} used to set
 * {@link org.jboss.as.cli.CommandContext} on {@code CLIOptionActivator}
 * instance.
 *
 * @author jdenise@redhat.com
 */
public class CLIOptionActivatorProvider implements OptionActivatorProvider {

    private final CommandContextImpl commandContext;

    public CLIOptionActivatorProvider(CommandContextImpl commandContext) {
        this.commandContext = commandContext;
    }

    @Override
    public OptionActivator enhanceOptionActivator(OptionActivator optionActivator) {

        if (optionActivator instanceof CLIOptionActivator) {
            ((CLIOptionActivator) optionActivator).setCommandContext(commandContext.newTimeoutCommandContext());
        }

        return optionActivator;
    }
}
