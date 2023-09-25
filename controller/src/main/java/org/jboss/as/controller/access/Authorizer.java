/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access;

import java.util.Set;

import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Interface exposed by the enforcement point in a WildFly access control system.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface Authorizer {

    /**
     * Gets a description of the characteristics of this authorizer
     *
     * @return the description. Cannot be {@code null}
     */
    AuthorizerDescription getDescription();

    /**
     * Authorize a management operation affecting an individual attribute.
     *
     * @param identity the caller identity. Cannot be {@code null}
     * @param callEnvironment  the call environment. Cannot be {@code null}
     * @param action the action being authorized. Cannot be {@code null}
     * @param target the target of the action. Cannot be {@code null}
     * @return the authorization result. Will not be {@code null}
     */
    AuthorizationResult authorize(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute target);

    /**
     * Authorize a management operation affecting an entire resource.
     *
     * @param identity the identity. Cannot be {@code null}
     * @param callEnvironment  the call environment. Cannot be {@code null}
     * @param action the action being authorized. Cannot be {@code null}
     * @param target the target of the action. Cannot be {@code null}
     * @return the authorization result. Will not be {@code null}
     */
    AuthorizationResult authorize(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource target);

    /**
     * Authorize a JMX operation. This operation should NOT be called for the management facade MBeans
     *
     * @param identity the caller identity. Cannot be {@code null}
     * @param callEnvironment the call environment. Cannot be {@code null}
     * @param action the action being authorized. Cannot be {@code null}
     * @param target the target of the action. Cannot be {@code null}
     * @return the authorization result. Will not be {@code null}
     */
    AuthorizationResult authorizeJmxOperation(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target);

    /**
     * Gets the set of roles the caller can run as taking into account any requested 'run as' roles.
     *
     *
     * @param identity the caller identity. Cannot be {@code null}
     * @param callEnvironment the call environment. Cannot be {@code null}
     * @param runAsRoles any requested 'run as' roles. May be {@code null}
     *
     * @return The set of roles assigned to the caller; an empty set will be returned if no roles are assigned or {@code null}
     *         will be returned if the access control provider does not support role mapping.
     */
    Set<String> getCallerRoles(SecurityIdentity identity, Environment callEnvironment, Set<String> runAsRoles);

    /**
     * Description of standard information about the custom authorizer.
     */
    interface AuthorizerDescription {

        /**
         * Gets whether the authorizer uses a role-based authorization mechanism.
         *
         * @return {@code true} if a role-based mechanism is used; {@code false} if not
         */
        boolean isRoleBased();

        /**
         * Gets the names of the "standard" built-in roles used by the authorizer. A built-in role requires no
         * end user configuration.
         *
         * @return the standard role names. Will not be {@code null}, but may be an empty set if roles are not used
         *         or no built-in roles are used.
         */
        Set<String> getStandardRoles();

    }
}
