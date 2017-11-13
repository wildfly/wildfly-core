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
