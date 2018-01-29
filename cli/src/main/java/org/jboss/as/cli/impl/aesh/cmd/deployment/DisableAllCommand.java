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
 * Disable all enabled deployments. All fields are public to be accessible from
 * legacy commands. To be made private when legacies are removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "disable-all", description = "", activator = ControlledCommandActivator.class)
public class DisableAllCommand extends AbstractUndeployCommand {

    // Public for compat reason. Make it private when removing compat code.
    public DisableAllCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, permissions);
    }

    @Override
    protected String getCommandName() {
        return "disable-all";
    }

    @Override
    protected String getName() {
        return "*";
    }

    @Override
    protected boolean keepContent() {
        return true;
    }

}
