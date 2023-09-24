/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security;

import static org.jboss.as.domain.management.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.domain.management.ModelDescriptionConstants.IDENTITY;
import static org.jboss.as.domain.management.ModelDescriptionConstants.MAPPED_ROLES;
import static org.jboss.as.domain.management.ModelDescriptionConstants.ROLES;
import static org.jboss.as.domain.management.ModelDescriptionConstants.USERNAME;
import static org.jboss.as.domain.management.ModelDescriptionConstants.WHOAMI;

import java.security.Principal;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Authorizer;
import org.jboss.as.controller.access.rbac.RunAsRoleMapper;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.domain.management.ModelDescriptionConstants;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.authz.Attributes;
import org.wildfly.security.authz.Roles;

/**
 * The OperationStepHandler for the whoami operation.
 *
 * The whoami operation allows for clients to request information about the currently authenticated user from the server, in
 * it's short form this will just return the username but in the verbose form this will also reveal the roles.
 *
 * This operation is needed as there are various scenarios where the client is not directly involved in the authentication
 * process from the admin console leaving the web browser to authenticate to the more silent mechanisms such as Kerberos and
 * JBoss Local User.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class WhoAmIOperation implements OperationStepHandler {

    private static final SimpleAttributeDefinition VERBOSE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.VERBOSE, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(WHOAMI, ControllerResolver.getResolver("core", "management"))
            .setParameters(VERBOSE)
            .setReadOnly()
            .setReplyType(ModelType.STRING)
            .build();

    private final Authorizer authorizer;

    private WhoAmIOperation(final Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    /**
     * @see org.jboss.as.controller.OperationStepHandler#execute(org.jboss.as.controller.OperationContext,
     *      org.jboss.dmr.ModelNode)
     */
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        boolean verbose = VERBOSE.resolveModelAttribute(context, operation).asBoolean();

        SecurityIdentity securityIdentity = context.getSecurityIdentity();
        if (securityIdentity == null) {
            throw new OperationFailedException(DomainManagementLogger.ROOT_LOGGER.noSecurityContextEstablished());
        }

        ModelNode result = context.getResult();
        ModelNode identity = result.get(IDENTITY);
        Principal principal = securityIdentity.getPrincipal();
        identity.get(USERNAME).set(principal.getName());

        if (verbose) {
            Roles roles = securityIdentity.getRoles();
            if (roles.isEmpty() == false) {
                ModelNode rolesModel = result.get(ROLES);
                for (String s : roles) {
                    rolesModel.add(s);
                }
            }

            Attributes attributes = securityIdentity.getAttributes();
            if (attributes.isEmpty() == false) {
                ModelNode attributesModel = result.get(ATTRIBUTES);
                for (Attributes.Entry e : attributes.entries()) {
                    ModelNode entry = attributesModel.get(e.getKey());
                    for (String s : e) {
                        entry.add(s);
                    }
                }
            }

            Set<String> mappedRoles = authorizer == null ? null : authorizer.getCallerRoles(context.getSecurityIdentity(), context.getCallEnvironment(), RunAsRoleMapper.getOperationHeaderRoles(operation));
            if (mappedRoles != null) {
                ModelNode rolesModel = result.get(MAPPED_ROLES);
                for (String current : mappedRoles) {
                    rolesModel.add(current);
                }
            }
        }

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    public static OperationStepHandler createOperation(final Authorizer authorizer) {
        return new WhoAmIOperation(authorizer);
    }

}
