/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh.activator;

import org.jboss.as.cli.CommandContext;

/**
 * Base class to develop a CLI command activator. It implements
 * {@link org.wildfly.core.cli.command.aesh.activator.CLICommandActivator}.
 *
 * @author jfdenise
 */
public abstract class AbstractCommandActivator implements CLICommandActivator {

    private CommandContext ctx;

    protected AbstractCommandActivator() {
    }

    @Override
    public void setCommandContext(CommandContext commandContext) {
        this.ctx = commandContext;
    }

    @Override
    public CommandContext getCommandContext() {
        return ctx;
    }
}
