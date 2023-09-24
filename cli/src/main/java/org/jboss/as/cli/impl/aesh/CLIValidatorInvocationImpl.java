/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import org.wildfly.core.cli.command.aesh.CLIValidatorInvocation;
import org.aesh.readline.AeshContext;
import org.jboss.as.cli.CommandContext;

/**
 * A concrete {@code CLIValidatorInvocation}.
 *
 * @author jdenise@redhat.com
 */
public class CLIValidatorInvocationImpl implements CLIValidatorInvocation {

    private CommandContext commandContext;
    private Object value;
    private Object command;
    private AeshContext aeshContext;

    public CLIValidatorInvocationImpl(CommandContext commandContext, Object value,
            AeshContext aeshContext, Object command) {
        this.commandContext = commandContext;
        this.value = value;
        this.aeshContext = aeshContext;
        this.command = command;
    }

    @Override
    public CommandContext getCommandContext() {
        return commandContext;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public Object getCommand() {
        return command;
    }

    @Override
    public AeshContext getAeshContext() {
        return aeshContext;
    }
}
