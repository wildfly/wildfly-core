/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.Locale;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.RollbackHandler;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;

/**
 * A {@link OperationStepHandler} for adding a role mapping.
 *
 * Initially this is just creating the resource in the model but will be updated later for additional functionality.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RoleMappingRemove implements OperationStepHandler {

    private final WritableAuthorizerConfiguration authorizerConfiguration;

    private RoleMappingRemove(final WritableAuthorizerConfiguration authorizerConfiguration) {
        this.authorizerConfiguration = authorizerConfiguration;
    }

    public static OperationStepHandler create(final WritableAuthorizerConfiguration authorizerConfiguration) {
        return new RoleMappingRemove(authorizerConfiguration);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.removeResource(PathAddress.EMPTY_ADDRESS);
        if (resource == null) {
            throw ControllerLogger.ROOT_LOGGER.managementResourceNotFound(context.getCurrentAddress());
        } else {
            PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
            final String roleName = address.getLastElement().getValue();

            RbacSanityCheckOperation.addOperation(context);

            registerRuntimeRemove(context, roleName.toUpperCase(Locale.ENGLISH));
        }
    }

    private void registerRuntimeRemove(final OperationContext context, final String roleName) {
        context.addStep(new OperationStepHandler() {

            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                Object undoKey = authorizerConfiguration.removeRoleMapping(roleName);
                if (undoKey == null) {
                    context.restartRequired();
                    throw DomainManagementLogger.ROOT_LOGGER.inconsistentRbacRuntimeState();
                }

                registerRollbackHandler(context, undoKey);
            }
        }, Stage.RUNTIME);
    }

    private void registerRollbackHandler(final OperationContext context, final Object undoKey) {
        context.completeStep(new RollbackHandler() {

            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                if (authorizerConfiguration.undoRoleMappingRemove(undoKey) == false) {
                    context.restartRequired();
                }
            }
        });

    }

}
