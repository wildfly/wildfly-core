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
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;

/**
 * Disable an enabled deployment.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "disable", description = "", activator = ControlledCommandActivator.class)
public class DisableCommand extends UndeployCommand {

    // Public for compat reason. Make it private when removing compat code.
    public DisableCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, permissions);
    }

    @Deprecated
    public DisableCommand(CommandContext ctx) {
        this(ctx, null);
    }

    @Override
    protected boolean keepContent() {
        return true;
    }

    @Override
    protected String getCommandName() {
        return "disable";
    }

}
