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
