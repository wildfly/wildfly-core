/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import java.util.Set;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.Authorizer;
import org.jboss.as.controller.access.CustomAuthorizer;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.rbac.StandardRBACAuthorizer;
import org.jboss.as.controller.access.rbac.SuperUserRoleMapper;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * A {@link org.jboss.as.controller.access.Authorizer} that delegates to another. Used for initial boot to allow
 * an instance of this class to be provided to the {@code ModelController} but then have the
 * functional implementation swapped out when boot proceeds to the point where the user-configured
 * authorizer is available.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public final class DelegatingConfigurableAuthorizer implements JmxAuthorizer {

    private final WritableAuthorizerConfiguration writableAuthorizerConfiguration;
    private volatile Authorizer delegate;

    public DelegatingConfigurableAuthorizer() {
        this.writableAuthorizerConfiguration =
                new WritableAuthorizerConfiguration(StandardRBACAuthorizer.AUTHORIZER_DESCRIPTION);
        this.delegate = StandardRBACAuthorizer.create(writableAuthorizerConfiguration,
                new SuperUserRoleMapper(writableAuthorizerConfiguration));
    }

    public WritableAuthorizerConfiguration getWritableAuthorizerConfiguration() {
        return writableAuthorizerConfiguration;
    }

    public void setDelegate(Authorizer delegate) {
        assert delegate != null : "null delegate";
        Authorizer currentDelegate = this.delegate;
        if (delegate instanceof CustomAuthorizer) {
            AuthorizerDescription description = ((CustomAuthorizer) delegate).setAuthorizerConfiguration(writableAuthorizerConfiguration);
            writableAuthorizerConfiguration.setAuthorizerDescription(description);
        } else {
            writableAuthorizerConfiguration.setAuthorizerDescription(delegate.getDescription());
        }
        this.delegate = delegate;

        if (currentDelegate instanceof CustomAuthorizer) {
            ((CustomAuthorizer) currentDelegate).shutdown();
        } else if (currentDelegate instanceof StandardRBACAuthorizer) {
            ((StandardRBACAuthorizer) currentDelegate).shutdown();
        }
    }

    @Override
    public Set<String> getCallerRoles(SecurityIdentity identity, Environment callEnvironment, Set<String> runAsRoles) {
        return delegate.getCallerRoles(identity, callEnvironment, runAsRoles);
    }

    @Override
    public AuthorizerDescription getDescription() {
        return delegate.getDescription();
    }

    @Override
    public AuthorizationResult authorize(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute target) {
        return delegate.authorize(identity, callEnvironment, action, target);
    }

    @Override
    public AuthorizationResult authorize(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource target) {
        return delegate.authorize(identity, callEnvironment, action, target);
    }

    @Override
    public AuthorizationResult authorizeJmxOperation(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target) {
        return delegate.authorizeJmxOperation(identity, callEnvironment, action, target);
    }

    @Override
    public void setNonFacadeMBeansSensitive(boolean sensitive) {
        writableAuthorizerConfiguration.setNonFacadeMBeansSensitive(sensitive);
    }

    public void shutdown() {
        if (delegate instanceof CustomAuthorizer) {
            ((CustomAuthorizer) delegate).shutdown();
        } else if (delegate instanceof StandardRBACAuthorizer) {
            ((StandardRBACAuthorizer) delegate).shutdown();
        }
    }

    @Override
    public boolean isNonFacadeMBeansSensitive() {
        return writableAuthorizerConfiguration.isNonFacadeMBeansSensitive();
    }

}
