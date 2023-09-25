/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.Locale;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.RollbackHandler;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;

/**
 * A {@link OperationStepHandler} for removing principals from the include / exclude list.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PrincipalRemove implements OperationStepHandler {

    private final WritableAuthorizerConfiguration authorizerConfiguration;
    private final WritableAuthorizerConfiguration.MatchType matchType;

    private PrincipalRemove(final WritableAuthorizerConfiguration authorizerConfiguration, final WritableAuthorizerConfiguration.MatchType matchType) {
        this.authorizerConfiguration = authorizerConfiguration;
        this.matchType = matchType;
    }

    public static OperationStepHandler createForInclude(final WritableAuthorizerConfiguration authorizerConfiguration) {
        return new PrincipalRemove(authorizerConfiguration, WritableAuthorizerConfiguration.MatchType.INCLUDE);
    }

    public static OperationStepHandler createForExclude(final WritableAuthorizerConfiguration authorizerConfiguration) {
        return new PrincipalRemove(authorizerConfiguration, WritableAuthorizerConfiguration.MatchType.EXCLUDE);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();

        final String roleName = RoleMappingResourceDefinition.getRoleName(operation);
        final AuthorizerConfiguration.PrincipalType principalType = PrincipalResourceDefinition.getPrincipalType(context, model);
        final String realm = PrincipalResourceDefinition.getRealm(context, model);
        final String name = PrincipalResourceDefinition.getName(context, model);

        context.removeResource(PathAddress.EMPTY_ADDRESS);

        if (matchType == WritableAuthorizerConfiguration.MatchType.INCLUDE) {
            RbacSanityCheckOperation.addOperation(context);
        }

        registerRuntimeRemove(context, roleName.toUpperCase(Locale.ENGLISH), principalType, name, realm);
    }

    private void registerRuntimeRemove(final OperationContext context, final String roleName,
            final AuthorizerConfiguration.PrincipalType principalType, final String name, final String realm) {
        /*
         * The address of the resource whilst hopefully being related to the attributes of the Principal resource is not
         * guaranteed, a unique name is needed but not one attribute can be regarded as being suitable as a unique key.
         */
        context.addStep(new OperationStepHandler() {

            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                if (authorizerConfiguration.removeRoleMappingPrincipal(roleName, principalType, matchType, name, realm)) {
                    registerRollbackHandler(context, roleName, principalType, name, realm);
                } else {
                    context.restartRequired();
                    throw DomainManagementLogger.ROOT_LOGGER.inconsistentRbacRuntimeState();
                }
            }
        }, Stage.RUNTIME);
    }

    private void registerRollbackHandler(final OperationContext context, final String roleName,
            final AuthorizerConfiguration.PrincipalType principalType, final String name, final String realm) {
        context.completeStep(new RollbackHandler() {

            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                if (authorizerConfiguration.addRoleMappingPrincipal(roleName, principalType, matchType, name, realm, false) == false) {
                    context.restartRequired();
                }
            }
        });
    }

}
