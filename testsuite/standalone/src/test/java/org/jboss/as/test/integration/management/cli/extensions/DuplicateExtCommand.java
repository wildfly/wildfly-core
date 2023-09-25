/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli.extensions;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * A command based on aesh command API that conflicts with batch builtin
 * command.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "batch", description = "")
public class DuplicateExtCommand implements Command<CLICommandInvocation> {

    public static final String OUTPUT = "Very useless";
    public static final String NAME = "batch";

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        commandInvocation.print(OUTPUT);
        return CommandResult.SUCCESS;
    }

}
