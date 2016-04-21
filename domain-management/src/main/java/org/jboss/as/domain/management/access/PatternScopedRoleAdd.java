/*
Copyright 2016 Red Hat, Inc.

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

package org.jboss.as.domain.management.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.constraint.PatternScopedConstraint;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handles the {@code add} operation for a {@link PatternScopedRolesResourceDefinition pattern scoped role}.
 *
 * @author Brian Stansberry
 */
public class PatternScopedRoleAdd extends ScopedRoleAddHandler {

    private final Map<String, PatternScopedConstraint> constraintMap;
    private final WritableAuthorizerConfiguration authorizerConfiguration;

    PatternScopedRoleAdd(Map<String, PatternScopedConstraint> constraintMap, WritableAuthorizerConfiguration authorizerConfiguration) {
        super(authorizerConfiguration, PatternScopedRolesResourceDefinition.BASE_ROLE, PatternScopedRolesResourceDefinition.PATTERNS);
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

        String baseRole = PatternScopedRolesResourceDefinition.BASE_ROLE.resolveModelAttribute(context, model).asString();

        ModelNode patternsAttribute = PatternScopedRolesResourceDefinition.PATTERNS.resolveModelAttribute(context, model);
        List<ModelNode> nodeList = patternsAttribute.isDefined() ? patternsAttribute.asList() : Collections.<ModelNode>emptyList();
        List<Pattern> patterns;
        try {
            patterns = stringsToPatterns(nodeList);
        } catch (PatternSyntaxException e) {
            throw new OperationFailedException(e);
        }
        addScopedRole(roleName, baseRole, patterns, authorizerConfiguration, constraintMap);
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {

        String roleName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        authorizerConfiguration.removeScopedRole(roleName);
        constraintMap.remove(roleName);
    }

    static List<Pattern> stringsToPatterns(List<ModelNode> patternNodes) throws PatternSyntaxException {

        List<Pattern> patterns = new ArrayList<>();
        for (ModelNode pattern : patternNodes) {
            patterns.add(Pattern.compile(pattern.asString()));
        }
        return patterns;
    }

    static void addScopedRole(final String roleName, final String baseRole, final List<Pattern> patterns,
                              final WritableAuthorizerConfiguration authorizerConfiguration,
                              final Map<String, PatternScopedConstraint> constraintMap) {

        PatternScopedConstraint constraint = new PatternScopedConstraint(patterns);
        authorizerConfiguration.addScopedRole(new AuthorizerConfiguration.ScopedRole(roleName, baseRole, constraint));
        constraintMap.put(roleName, constraint);
    }
}
