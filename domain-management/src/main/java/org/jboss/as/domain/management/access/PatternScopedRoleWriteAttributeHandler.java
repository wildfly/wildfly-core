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

import static org.jboss.as.domain.management.access.PatternScopedRoleAdd.stringsToPatterns;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.constraint.PatternScopedConstraint;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Handles the {@code write-attribute} operation for a {@link PatternScopedRolesResourceDefinition pattern scoped role}.
 *
 * @author Brian Stansberry
 */
public class PatternScopedRoleWriteAttributeHandler extends AbstractWriteAttributeHandler<Void> {

    private final Map<String, PatternScopedConstraint> constraintMap;

    PatternScopedRoleWriteAttributeHandler(Map<String, PatternScopedConstraint> constraintMap) {
        super(PatternScopedRolesResourceDefinition.PATTERNS);
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

    private void applyChangeToConstraint(final ModelNode operation, final ModelNode resolvedValue) throws OperationFailedException {

        final String roleName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        PatternScopedConstraint constraint = constraintMap.get(roleName);

        // null means the resource shouldn't exist and we should have failed in Stage.MODEL
        assert constraint != null : "unknown role " + roleName;

        List<Pattern> patterns;
        if (resolvedValue.isDefined()) {
            try {
                patterns = stringsToPatterns(resolvedValue.asList());
            } catch (PatternSyntaxException e) {
                throw new OperationFailedException(e);
            }
        } else {
            patterns = Collections.emptyList();
        }

        constraint.setAllowedPatterns(patterns);
    }
}
