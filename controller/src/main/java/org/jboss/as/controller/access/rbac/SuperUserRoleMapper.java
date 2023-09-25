/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.logging.ControllerLogger;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * A {@link RoleMapper} that always maps the user to the role SUPERUSER.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SuperUserRoleMapper implements RoleMapper {

    private final Set<String> SUPERUSER = Collections.singleton(StandardRole.SUPERUSER.toString());

    private final AuthorizerConfiguration authorizerConfiguration;
    public SuperUserRoleMapper(AuthorizerConfiguration configuration) {
        authorizerConfiguration = configuration;
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute attribute) {
        return SUPERUSER;
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource resource) {
        return SUPERUSER;
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target) {
        return SUPERUSER;
    }

    @Override
    public Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Set<String> operationHeaderRoles) {
        return SUPERUSER;
    }

    @Override
    public boolean canRunAs(Set<String> mappedRoles, String runAsRole) {
        if (runAsRole == null) {
            return false;
        }

        boolean hasRole = authorizerConfiguration.hasRole(runAsRole);
        boolean isSuperUser = mappedRoles.contains(StandardRole.SUPERUSER.toString());

        /*
         * We only allow users to specify roles to run as if they are SuperUser, if the user is not SuperUser we need to be
         * careful to not provide a way for the user to test which roles actually exist.
         */

        if (isSuperUser && hasRole == false) {
            throw ControllerLogger.ROOT_LOGGER.unknownRole(runAsRole);
        }

        return hasRole && isSuperUser;
    }

}
