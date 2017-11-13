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
