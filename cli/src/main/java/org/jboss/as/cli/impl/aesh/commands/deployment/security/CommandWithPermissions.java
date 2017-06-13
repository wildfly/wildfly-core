/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl.aesh.commands.deployment.security;

import java.util.function.Function;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.impl.aesh.commands.security.AbstractControlledCommand;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;

/**
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
