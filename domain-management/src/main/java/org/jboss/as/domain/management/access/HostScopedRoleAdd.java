/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.constraint.HostEffectConstraint;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handles the {@code add} operation for a {@link HostScopedRolesResourceDefinition host scoped role}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
class HostScopedRoleAdd extends ScopedRoleAddHandler {

    private final Map<String, HostEffectConstraint> constraintMap;
    private final WritableAuthorizerConfiguration authorizerConfiguration;

    HostScopedRoleAdd(Map<String, HostEffectConstraint> constraintMap, WritableAuthorizerConfiguration authorizerConfiguration) {
        super(authorizerConfiguration);
        this.constraintMap = constraintMap;
        this.authorizerConfiguration = authorizerConfiguration;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {

        String roleName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();

        String baseRole = HostScopedRolesResourceDefinition.BASE_ROLE.resolveModelAttribute(context, model).asString();

        ModelNode hostsAttribute = HostScopedRolesResourceDefinition.HOSTS.resolveModelAttribute(context, model);
        List<ModelNode> nodeList = hostsAttribute.isDefined() ? hostsAttribute.asList() : Collections.<ModelNode>emptyList();

        addScopedRole(roleName, baseRole, nodeList, authorizerConfiguration, constraintMap);
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {

        String roleName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        authorizerConfiguration.removeScopedRole(roleName);
        constraintMap.remove(roleName);
    }

    static void addScopedRole(final String roleName, final String baseRole, final List<ModelNode> hostNodes,
                              final WritableAuthorizerConfiguration authorizerConfiguration,
                              final Map<String, HostEffectConstraint> constraintMap) {

        List<String> hosts = new ArrayList<String>();
        for (ModelNode host : hostNodes) {
            hosts.add(host.asString());
        }
        HostEffectConstraint constraint = new HostEffectConstraint(hosts);
        authorizerConfiguration.addScopedRole(new AuthorizerConfiguration.ScopedRole(roleName, baseRole, constraint));
        constraintMap.put(roleName, constraint);
    }
}

