/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli.extensions;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * A command based on aesh command API.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "useless", description = "")
public class CliExtCommand implements Command<CLICommandInvocation> {

    public static final String OUTPUT = "Very useless";
    public static final String NAME = "useless";

    @Option
    private boolean myoption;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        commandInvocation.print(OUTPUT);
        return CommandResult.SUCCESS;
    }

}
