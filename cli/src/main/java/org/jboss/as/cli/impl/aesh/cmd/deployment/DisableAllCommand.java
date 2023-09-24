/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
