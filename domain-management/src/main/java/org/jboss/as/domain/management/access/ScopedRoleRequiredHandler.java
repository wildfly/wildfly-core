/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP_SCOPED_ROLE;

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
 * An {@link OperationStepHandler} to be executed at the end of stage MODEL to confirm that a scoped role of the name specified
 * does exist.
 *
 * This is used in domain mode where a non-standard role mapping is added, we need to be sure the scoped role does actually
 * exist.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ScopedRoleRequiredHandler implements OperationStepHandler {

    private final String roleName;

    private ScopedRoleRequiredHandler(final String roleName) {
        this.roleName = roleName;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        Set<String> hostScopedRoles = resource.getChildrenNames(HOST_SCOPED_ROLE);
        Set<String> serverGroupScopedRoles = resource.getChildrenNames(SERVER_GROUP_SCOPED_ROLE);

        if (hostScopedRoles.contains(roleName) == false && serverGroupScopedRoles.contains(roleName) == false) {
            throw DomainManagementLogger.ROOT_LOGGER.invalidRoleNameDomain(roleName);
        }
    }

    static void addOperation(OperationContext context, String roleName) {
        ModelNode operation = Util.createEmptyOperation("scoped-role-check", PathAddress.pathAddress(
                CoreManagementResourceDefinition.PATH_ELEMENT, AccessAuthorizationResourceDefinition.PATH_ELEMENT));

        context.addStep(operation, new ScopedRoleRequiredHandler(roleName), Stage.MODEL);
    }

}
