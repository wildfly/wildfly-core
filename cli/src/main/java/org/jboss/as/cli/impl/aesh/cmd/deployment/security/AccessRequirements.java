/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment.security;

import java.util.function.Function;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;

/**
 * Utility class to build {@code AccessRequirement} needed for various
 * deployment commands.
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
