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
