/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment.security;

import java.util.function.Function;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.impl.aesh.cmd.security.AbstractControlledCommand;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;

/**
 * An abstract deployment related command that depends on some permissions.
 *
 * @author jdenise@redhat.com
 */
public abstract class CommandWithPermissions extends AbstractControlledCommand {

    private final Permissions permissions;

    public CommandWithPermissions(CommandContext ctx, Function<CommandContext, AccessRequirement> ac,
            Permissions permissions) {
        super(ctx, ac);
        this.permissions = permissions;
        DefaultOperationRequestAddress requiredAddress
                = new DefaultOperationRequestAddress();
        requiredAddress.toNodeType(Util.DEPLOYMENT);
        addRequiredPath(requiredAddress);
    }

    public Permissions getPermissions() {
        return permissions;
    }

}
