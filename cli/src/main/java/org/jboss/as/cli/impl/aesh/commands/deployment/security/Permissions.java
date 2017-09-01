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

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;
import org.jboss.as.cli.accesscontrol.PerNodeOperationAccess;

/**
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
