/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.dmr.ModelNode;

/**
 * Handlers for reading the lists of roles from the {@link AuthorizerConfiguration}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
abstract class AccessAuthorizationRolesHandler implements OperationStepHandler {

    static AccessAuthorizationRolesHandler getStandardRolesHandler(AuthorizerConfiguration authorizerConfiguration) {
        return new Standard(authorizerConfiguration);
    }

    static AccessAuthorizationRolesHandler getAllRolesHandler(AuthorizerConfiguration authorizerConfiguration) {
        return new All(authorizerConfiguration);
    }

    final AuthorizerConfiguration authorizerConfiguration;

    AccessAuthorizationRolesHandler(AuthorizerConfiguration configuration) {
        authorizerConfiguration = configuration;
    }


    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        Set<String> list = getRolesList();
        ModelNode result = context.getResult().setEmptyList();
        for (String role : list) {
            result.add(role);
        }
    }

    abstract Set<String> getRolesList();

    private static class Standard extends AccessAuthorizationRolesHandler {

        private Standard(AuthorizerConfiguration configuration) {
            super(configuration);
        }

        @Override
        Set<String> getRolesList() {
            return authorizerConfiguration.getStandardRoles();
        }
    }

    private static class All extends AccessAuthorizationRolesHandler {

        private All(AuthorizerConfiguration configuration) {
            super(configuration);
        }

        @Override
        Set<String> getRolesList() {
            return authorizerConfiguration.getAllRoles();
        }
    }
}
