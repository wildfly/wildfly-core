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
