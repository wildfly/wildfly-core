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
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators;

/**
 * Undeploy a deployment. All fields are public to be accessible from legacy
 * commands. To be made private when legacies are removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "undeploy", description = "", activator = ControlledCommandActivator.class)
public class UndeployCommand extends AbstractUndeployCommand {

    // Argument comes first, aesh behavior.
    @Argument(required = true, activator = OptionActivators.UndeployNameActivator.class,
            completer = EnableCommand.NameCompleter.class)
    public String name;

    // Public for compat reason. Make it private when removing compat code.
    public UndeployCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, permissions);
    }

    @Deprecated
    public UndeployCommand(CommandContext ctx) {
        this(ctx, null);
    }

    @Override
    protected boolean keepContent() {
        return false;
    }

    @Override
    protected String getName() {
        return name;
    }

    @Override
    protected String getCommandName() {
        return "undeploy";
    }
}
