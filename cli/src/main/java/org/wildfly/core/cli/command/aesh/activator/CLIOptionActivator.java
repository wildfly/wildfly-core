/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh.activator;

import org.aesh.command.activator.OptionActivator;
import org.jboss.as.cli.CommandContext;

/**
 * An {@code OptionActivator} that exposes
 * {@link org.jboss.as.cli.CommandContext}.
 *
 * @author jdenise@redhat.com
 */
public interface CLIOptionActivator extends OptionActivator {
    /**
     * Called internally by the CLI in order to set the {@code CommandContext}
     *
     * @param commandContext The {@code CommandContext} instance.
     */
    void setCommandContext(CommandContext commandContext);

    CommandContext getCommandContext();
}
