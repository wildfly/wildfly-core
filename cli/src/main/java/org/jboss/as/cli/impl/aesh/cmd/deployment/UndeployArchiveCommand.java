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
 * Undeploy using a CLI archive file (.cli file).
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "undeploy-cli-archive", description = "", activator = ControlledCommandActivator.class)
public class UndeployArchiveCommand extends DeployArchiveCommand {

    public UndeployArchiveCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, permissions);
    }

    @Deprecated
    public UndeployArchiveCommand(CommandContext ctx) {
        super(ctx, null);
    }

    @Override
    protected String getAction() {
        return "undeploy-cli-archive";
    }

    @Override
    protected String getDefaultScript() {
        return "undeploy.scr";
    }
}
