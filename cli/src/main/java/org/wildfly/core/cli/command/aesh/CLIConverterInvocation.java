/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh;

import org.aesh.readline.AeshContext;
import org.aesh.command.converter.ConverterInvocation;
import org.jboss.as.cli.CommandContext;

/**
 * CLI specific {@code ConverterInvocation} that exposes
 * {@link org.jboss.as.cli.CommandContext}.
 *
 * @author jdenise@redhat.com
 */
public class CLIConverterInvocation implements ConverterInvocation {

    private final CommandContext commandContext;
    private final String input;
    private final AeshContext aeshContext;

    public CLIConverterInvocation(CommandContext commandContext,
            AeshContext aeshContext, String input) {
        this.input = input;
        this.commandContext = commandContext;
        this.aeshContext = aeshContext;
    }

    @Override
    public String getInput() {
        return input;
    }

    @Override
    public AeshContext getAeshContext() {
        return aeshContext;
    }

    public CommandContext getCommandContext() {
        return commandContext;
    }
}
