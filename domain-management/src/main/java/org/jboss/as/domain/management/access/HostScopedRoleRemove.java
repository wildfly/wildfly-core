/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.constraint.HostEffectConstraint;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handles the {@code remove} operation for a {@link HostScopedRolesResourceDefinition host scoped role}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
class HostScopedRoleRemove implements OperationStepHandler {

    private final Map<String, HostEffectConstraint> constraintMap;
    private final WritableAuthorizerConfiguration authorizerConfiguration;

    HostScopedRoleRemove(Map<String, HostEffectConstraint> constraintMap,
                         WritableAuthorizerConfiguration authorizerConfiguration) {
        this.constraintMap = constraintMap;
        this.authorizerConfiguration = authorizerConfiguration;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode model = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));

        context.removeResource(PathAddress.EMPTY_ADDRESS);

        final String roleName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        RoleMappingNotRequiredHandler.addOperation(context, roleName);

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                final String baseRole = ServerGroupScopedRoleResourceDefinition.BASE_ROLE.resolveModelAttribute(context, model).asString();
                ModelNode hostsAttribute = HostScopedRolesResourceDefinition.HOSTS.resolveModelAttribute(context, model);
                final List<ModelNode> hostNodes = hostsAttribute.isDefined() ? hostsAttribute.asList() : Collections.<ModelNode>emptyList();

                authorizerConfiguration.removeScopedRole(roleName);
                constraintMap.remove(roleName);
                context.completeStep(new OperationContext.RollbackHandler() {
                    @Override
                    public void handleRollback(OperationContext context, ModelNode operation) {
                        HostScopedRoleAdd.addScopedRole(roleName, baseRole, hostNodes, authorizerConfiguration, constraintMap);
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }
}

