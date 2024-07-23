/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.constraint.HostEffectConstraint;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Handles the {@code write-attribute} operation for a {@link HostScopedRolesResourceDefinition host scoped role}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
class HostScopedRoleWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    private final Map<String, HostEffectConstraint> constraintMap;

    HostScopedRoleWriteAttributeHandler(Map<String, HostEffectConstraint> constraintMap) {
        this.constraintMap = constraintMap;
    }


    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {

        applyChangeToConstraint(operation, resolvedValue);

        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        applyChangeToConstraint(operation, valueToRestore);
    }

    private void applyChangeToConstraint(final ModelNode operation, final ModelNode resolvedValue) {

        final String roleName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        HostEffectConstraint constraint = constraintMap.get(roleName);

        // null means the resource shouldn't exist and we should have failed in Stage.MODEL
        assert constraint != null : "unknown role " + roleName;

        List<String> hosts = new ArrayList<String>();
        if (resolvedValue.isDefined()) {
            for (ModelNode host : resolvedValue.asList()) {
                hosts.add(host.asString());
            }
        }

        constraint.setAllowedHosts(hosts);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }
}

