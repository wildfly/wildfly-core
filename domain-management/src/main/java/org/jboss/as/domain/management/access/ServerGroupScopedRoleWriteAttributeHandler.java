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
import org.jboss.as.controller.access.constraint.ServerGroupEffectConstraint;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Handles the {@code write-attribute} operation for a {@link ServerGroupScopedRoleResourceDefinition server group scoped role}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
class ServerGroupScopedRoleWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    private final Map<String, ServerGroupEffectConstraint> constraintMap;

    public ServerGroupScopedRoleWriteAttributeHandler(Map<String, ServerGroupEffectConstraint> constraintMap) {
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
        ServerGroupEffectConstraint constraint = constraintMap.get(roleName);

        // null could happen if the role was removed in a later step in a composite. unlikely but possible
        if (constraint != null)  {

            List<String> serverGroups = new ArrayList<String>();
            for (ModelNode group : resolvedValue.asList()) {
                serverGroups.add(group.asString());
            }

            constraint.setAllowedGroups(serverGroups);
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }
}
