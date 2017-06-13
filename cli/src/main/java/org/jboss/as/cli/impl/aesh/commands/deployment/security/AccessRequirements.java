/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;

/**
 *
 * @author jdenise@redhat.com
 */
public final class AccessRequirements {

    public static Function<CommandContext, AccessRequirement> deployArchiveAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getFullReplacePermission())
                    .requirement(permissions.getAddOrReplacePermission())
                    .requirement(permissions.getDeployPermission())
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> undeployArchiveAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getMainRemovePermission())
                    .requirement(permissions.getUndeployPermission())
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> listAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getDeployPermission())
                    .requirement(permissions.getListPermission())
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> infoAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .standalone()
                    .any()
                    .operation(Util.READ_CHILDREN_RESOURCES)
                    .operation(Util.DEPLOYMENT + "=?", Util.READ_RESOURCE)
                    .parent()
                    .parent()
                    .domain()
                    .any()
                    .all()
                    .operation(Util.VALIDATE_ADDRESS)
                    .operation(Util.DEPLOYMENT + "=?", Util.READ_RESOURCE)
                    .serverGroupOperation(Util.DEPLOYMENT + "=?", Util.READ_RESOURCE)
                    .parent()
                    .all()
                    .operation(Util.READ_CHILDREN_RESOURCES)
                    .requirement(permissions.getSgChildrenResourcesPermission())
                    .parent()
                    .parent()
                    .parent()
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> enableAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getDeployPermission())
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> deployContentAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getFullReplacePermission())
                    .requirement(permissions.getAddOrReplacePermission())
                    .requirement(permissions.getDeployPermission())
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> undeployAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getMainRemovePermission())
                    .requirement(permissions.getUndeployPermission())
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> deploymentAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getListPermission())
                    .requirement(permissions.getFullReplacePermission())
                    .requirement(permissions.getMainAddPermission())
                    .requirement(permissions.getDeployPermission())
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> enableAllAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getDeployPermission())
                    .build();
        };
    }

    public static Function<CommandContext, AccessRequirement> undeployLegacyAccess(Permissions permissions) {
        return (ctx) -> {
            return AccessRequirementBuilder.Factory.create(ctx)
                    .any()
                    .requirement(permissions.getListPermission())
                    .requirement(permissions.getMainRemovePermission())
                    .requirement(permissions.getUndeployPermission())
                    .build();
        };
    }
}
