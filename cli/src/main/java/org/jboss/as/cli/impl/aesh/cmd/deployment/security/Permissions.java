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

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;
import org.jboss.as.cli.accesscontrol.PerNodeOperationAccess;

/**
 * Instance of this class contains all permissions tha tare in the scope of the
 * deployment.
 *
 * @author jdenise@redhat.com
 */
public class Permissions {

    private final AccessRequirement listPermission;
    private final AccessRequirement fullReplacePermission;
    private final AccessRequirement mainAddPermission;
    private final AccessRequirement deployPermission;
    private final AccessRequirement addOrReplacePermission;
    private final PerNodeOperationAccess serverGroupAddPermission;
    private final PerNodeOperationAccess sgChildrenResourcesPermission;
    private final AccessRequirement mainRemovePermission;
    private final AccessRequirement undeployPermission;
    private final AccessRequirement removeOrUndeployPermission;
    public Permissions(CommandContext ctx) {
        listPermission = AccessRequirementBuilder.Factory.create(ctx)
                .all()
                .operation(Util.READ_CHILDREN_NAMES)
                .operation("deployment=?", Util.READ_RESOURCE)
                .build();
        fullReplacePermission = AccessRequirementBuilder.Factory.create(ctx).any().
                operation(Util.FULL_REPLACE_DEPLOYMENT).build();
        mainAddPermission = AccessRequirementBuilder.Factory.create(ctx).any().
                operation("deployment=?", Util.ADD).build();
        serverGroupAddPermission = new PerNodeOperationAccess(ctx, Util.SERVER_GROUP,
                "deployment=?", Util.ADD);
        deployPermission = AccessRequirementBuilder.Factory.create(ctx)
                .any()
                .operation("deployment=?", Util.DEPLOY)
                .all()
                .requirement(serverGroupAddPermission)
                .serverGroupOperation("deployment=?", Util.DEPLOY)
                .parent()
                .build();
        sgChildrenResourcesPermission = new PerNodeOperationAccess(ctx,
                Util.SERVER_GROUP, null, Util.READ_CHILDREN_RESOURCES);
        addOrReplacePermission = AccessRequirementBuilder.Factory.create(ctx)
                .any()
                .requirement(mainAddPermission)
                .requirement(fullReplacePermission)
                .build();
        mainRemovePermission = AccessRequirementBuilder.Factory.create(ctx).
                any().operation("deployment=?", Util.REMOVE).build();

        undeployPermission = AccessRequirementBuilder.Factory.create(ctx)
                .any()
                .operation("deployment=?", Util.UNDEPLOY)
                .all()
                .serverGroupOperation("deployment=?", Util.REMOVE)
                .serverGroupOperation("deployment=?", Util.UNDEPLOY)
                .parent()
                .build();

        removeOrUndeployPermission = AccessRequirementBuilder.Factory.create(ctx)
                .any()
                .requirement(mainRemovePermission)
                .requirement(undeployPermission)
                .build();
    }

    /**
     * @return the listPermission
     */
    public AccessRequirement getListPermission() {
        return listPermission;
    }

    /**
     * @return the fullReplacePermission
     */
    public AccessRequirement getFullReplacePermission() {
        return fullReplacePermission;
    }

    /**
     * @return the mainAddPermission
     */
    public AccessRequirement getMainAddPermission() {
        return mainAddPermission;
    }

    /**
     * @return the deployPermission
     */
    public AccessRequirement getDeployPermission() {
        return deployPermission;
    }

    /**
     * @return the addOrReplacePermission
     */
    public AccessRequirement getAddOrReplacePermission() {
        return addOrReplacePermission;
    }

    /**
     * @return the serverGroupAddPermission
     */
    public PerNodeOperationAccess getServerGroupAddPermission() {
        return serverGroupAddPermission;
    }

    /**
     * @return the sgChildrenResourcesPermission
     */
    public PerNodeOperationAccess getSgChildrenResourcesPermission() {
        return sgChildrenResourcesPermission;
    }

    /**
     * @return the mainRemovePermission
     */
    public AccessRequirement getMainRemovePermission() {
        return mainRemovePermission;
    }

    /**
     * @return the undeployPermission
     */
    public AccessRequirement getUndeployPermission() {
        return undeployPermission;
    }

    /**
     * @return the removeOrUndeployPermission
     */
    public AccessRequirement getRemoveOrUndeployPermission() {
        return removeOrUndeployPermission;
    }
}
