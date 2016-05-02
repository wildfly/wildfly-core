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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.constraint.PatternScopedConstraint;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handles the {@code remove} operation for a {@link PatternScopedRolesResourceDefinition pattern scoped role}.
 *
 * @author Brian Stansberry
 */
public class PatternScopedRoleRemove  implements OperationStepHandler {

    private final Map<String, PatternScopedConstraint> constraintMap;
    private final WritableAuthorizerConfiguration authorizerConfiguration;

    PatternScopedRoleRemove(Map<String, PatternScopedConstraint> constraintMap,
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

                final String baseRole = PatternScopedRolesResourceDefinition.BASE_ROLE.resolveModelAttribute(context, model).asString();
                ModelNode patternsAttribute = PatternScopedRolesResourceDefinition.PATTERNS.resolveModelAttribute(context, model);
                final List<ModelNode> patternNodes = patternsAttribute.isDefined() ? patternsAttribute.asList() : Collections.<ModelNode>emptyList();

                authorizerConfiguration.removeScopedRole(roleName);
                constraintMap.remove(roleName);
                context.completeStep(new OperationContext.RollbackHandler() {
                    @Override
                    public void handleRollback(OperationContext context, ModelNode operation) {
                        List<Pattern> patterns = PatternScopedRoleAdd.stringsToPatterns(patternNodes);
                        PatternScopedRoleAdd.addScopedRole(roleName, baseRole, patterns, authorizerConfiguration, constraintMap);
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
