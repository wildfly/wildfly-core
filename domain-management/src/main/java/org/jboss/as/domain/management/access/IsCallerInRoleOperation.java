/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import static org.jboss.as.domain.management.ModelDescriptionConstants.IS_CALLER_IN_ROLE;

import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Authorizer;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.rbac.RunAsRoleMapper;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * A {@link org.jboss.as.controller.ResourceDefinition} representing an individual role mapping.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class IsCallerInRoleOperation implements OperationStepHandler {

    public static final SimpleOperationDefinition DEFINITION  = new SimpleOperationDefinitionBuilder(IS_CALLER_IN_ROLE, DomainManagementResolver.getResolver("core", "management", "access-control"))
            .setReplyType(ModelType.BOOLEAN)
            .setReadOnly()
            .build();

    private final Authorizer authorizer;

    private IsCallerInRoleOperation(final Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        String roleName = RoleMappingResourceDefinition.getRoleName(operation);

        if (context.getCurrentStage() == Stage.MODEL) {
            context.addStep(this, Stage.RUNTIME);
        } else {
            ModelNode result = context.getResult();
            Set<String> operationHeaderRoles = RunAsRoleMapper.getOperationHeaderRoles(operation);
            result.set(isCallerInRole(roleName, context.getSecurityIdentity(), context.getCallEnvironment(), operationHeaderRoles));
        }
    }

    private boolean isCallerInRole(String roleName, SecurityIdentity identity, Environment callEnvironment, Set<String> operationHeaderRoles) {
        Set<String> mappedRoles = authorizer.getCallerRoles(identity, callEnvironment, operationHeaderRoles);
        if (mappedRoles == null) {
            return false;
        } else if (mappedRoles.contains(roleName)) {
            return true;
        } else {
            for (String role : mappedRoles) {
                if (role.equalsIgnoreCase(roleName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static OperationStepHandler create(final Authorizer authorizer) {
        return new IsCallerInRoleOperation(authorizer);
    }

}
