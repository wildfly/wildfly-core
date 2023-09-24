/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;

import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;

/**
 * An {@link OperationStepHandler} to be executed at the end of stage MODEL to confirm that a role mapping does not exist.
 *
 * This is used in domain mode where a scoped role is removed to verify there is no remaining role mapping.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RoleMappingNotRequiredHandler implements OperationStepHandler {

    private final String roleName;

    private RoleMappingNotRequiredHandler(final String roleName) {
        this.roleName = roleName;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        Set<String> roleMappings = resource.getChildrenNames(ROLE_MAPPING);

        if (roleMappings.contains(roleName)) {
            throw DomainManagementLogger.ROOT_LOGGER.roleMappingRemaining(roleName);
        }
    }

    static void addOperation(OperationContext context, String roleName) {
        ModelNode operation = Util.createEmptyOperation("role-mapping-check", PathAddress.pathAddress(
                CoreManagementResourceDefinition.PATH_ELEMENT, AccessAuthorizationResourceDefinition.PATH_ELEMENT));

        context.addStep(operation, new RoleMappingNotRequiredHandler(roleName), Stage.MODEL);
    }

}
