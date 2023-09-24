/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;

import java.util.List;
import java.util.Locale;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.RollbackHandler;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration.MatchType;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * A {@link OperationStepHandler} for adding principals to the include / exclude list.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PrincipalAdd implements OperationStepHandler {

    private final WritableAuthorizerConfiguration authorizerConfiguration;
    private final WritableAuthorizerConfiguration.MatchType matchType;

    private PrincipalAdd(final WritableAuthorizerConfiguration authorizerConfiguration, final WritableAuthorizerConfiguration.MatchType matchType) {
        this.authorizerConfiguration = authorizerConfiguration;
        this.matchType = matchType;
    }

    public static OperationStepHandler createForInclude(final WritableAuthorizerConfiguration authorizerConfiguration) {
        return new PrincipalAdd(authorizerConfiguration, WritableAuthorizerConfiguration.MatchType.INCLUDE);
    }

    public static OperationStepHandler createForExclude(final WritableAuthorizerConfiguration authorizerConfiguration) {
        return new PrincipalAdd(authorizerConfiguration, WritableAuthorizerConfiguration.MatchType.EXCLUDE);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode model = context.createResource(PathAddress.EMPTY_ADDRESS).getModel();

        PrincipalResourceDefinition.TYPE.validateAndSet(operation, model);
        PrincipalResourceDefinition.REALM.validateAndSet(operation, model);
        PrincipalResourceDefinition.NAME.validateAndSet(operation, model);

        final String roleName = RoleMappingResourceDefinition.getRoleName(operation);
        final AuthorizerConfiguration.PrincipalType principalType = PrincipalResourceDefinition.getPrincipalType(context, model);
        final String realm = PrincipalResourceDefinition.getRealm(context, model);
        final String name = PrincipalResourceDefinition.getName(context, model);

        PathAddress completeAddress = PathAddress.pathAddress(operation.get(ADDRESS));
        PathAddress roleAddress = completeAddress.subAddress(0, completeAddress.size() - 1);
        validateUniqueness(context, roleName, roleAddress, principalType, realm, name);

        registerRuntimeAdd(context, roleName.toUpperCase(Locale.ENGLISH), principalType, name, realm);
    }

    private void validateUniqueness(final OperationContext context, final String roleName, final PathAddress roleAddress,
            final AuthorizerConfiguration.PrincipalType principalType, final String realm, final String name)
            throws OperationFailedException {
        Resource roleResource = context.readResourceFromRoot(roleAddress);
        ModelNode model = Resource.Tools.readModel(roleResource);

        int matchesFound = 0;
        for (Property current : getIncludeExclude(model)) {
            if (matches(context, current.getValue(), principalType, realm, name)) {
                if (++matchesFound > 1) {
                    throw DomainManagementLogger.ROOT_LOGGER.duplicateIncludeExclude(roleName, matchType.toString(), principalType.toString(), name, realm != null ? realm : "undefined");
                }
            }
        }
    }

    private boolean matches(final OperationContext context, final ModelNode value,
            final AuthorizerConfiguration.PrincipalType principalType, final String realm, final String name)
            throws OperationFailedException {
        final AuthorizerConfiguration.PrincipalType valuePrincipalType = PrincipalResourceDefinition.getPrincipalType(context,
                value);
        if (principalType != valuePrincipalType) {
            return false;
        }
        final String valueName = PrincipalResourceDefinition.getName(context, value);
        if (name.equals(valueName) == false) {
            return false;
        }
        final String valueRealm = PrincipalResourceDefinition.getRealm(context, value);
        if ((realm == null && valueRealm != null) ||
                (realm != null && realm.equals(valueRealm) == false)) {
            return false;
        }

        return true;
    }

    private List<Property> getIncludeExclude(final ModelNode model) {
        if (matchType == MatchType.INCLUDE) {
            return model.get(INCLUDE).asPropertyList();
        } else {
            return model.get(EXCLUDE).asPropertyList();
        }
    }

    private void registerRuntimeAdd(final OperationContext context, final String roleName, final AuthorizerConfiguration.PrincipalType principalType,
            final String name, final String realm) {
        /*
         * The address of the resource whilst hopefully being related to the attributes of the Principal resource is not
         * guaranteed, a unique name is needed but not one attribute can be regarded as being suitable as a unique key.
         */
        context.addStep(new OperationStepHandler() {

            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                if (authorizerConfiguration.addRoleMappingPrincipal(roleName, principalType, matchType, name, realm, context.isBooting())) {
                    registerRollbackHandler(context, roleName, principalType, name, realm);
                } else {
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
                if (authorizerConfiguration.removeRoleMappingPrincipal(roleName, principalType, matchType, name, realm) == false) {
                    context.restartRequired();
                }
            }
        });

    }

}
