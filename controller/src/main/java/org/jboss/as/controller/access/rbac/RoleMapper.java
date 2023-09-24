/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import java.util.Set;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Determines the set of roles applicable for a management request.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface RoleMapper {

    /**
     * Determine the roles available for the caller for a management operation affecting an individual attribute.
     *
     * @param identity the caller identity. Cannot be {@code null}
     * @param callEnvironment  the call environment. Cannot be {@code null}
     * @param action the action being authorized. Cannot be {@code null}
     * @param attribute the target of the action. Cannot be {@code null}
     * @return the roles. Will not be {@code null}, but may be an empty set
     */
    Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute attribute);

    /**
     * Determine the roles available for the caller for a management operation affecting an entire resource.
     *
     * @param identity the caller identity. Cannot be {@code null}
     * @param callEnvironment  the call environment. Cannot be {@code null}
     * @param action the action being authorized. Cannot be {@code null}
     * @param resource the target of the action. Cannot be {@code null}
     * @return the roles. Will not be {@code null}, but may be an empty set
     */
    Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource resource);

    /**
     * Determine the roles available for the caller for a JMX invocation unrelated to the management facade MBeans.
     *
     * @param identity the caller identity. Cannot be {@code null}
     * @param callEnvironment the call environment. Cannot be {@code null}
     * @param action the action being authorized. Cannot be {@code null}
     * @param target the target of the action. Cannot be {@code null}
     * @return the roles. Will not be {@code null}, but may be an empty set
     */
    Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target);

    /**
     * Determine the roles available for the caller without reference to a particular action or target. Note
     * that actually mapping a caller to roles without reference to a particular action or target is not required.
     *
     *
     * @param identity the caller identity. Cannot be {@code null}
     * @param callEnvironment  the call environment. Cannot be {@code null}
     * @param operationHeaderRoles any roles specified as headers in the operation. May be {@code null}
     * @return the roles. Will not be {@code null}, but may be an empty set
     */
    Set<String> mapRoles(SecurityIdentity identity, Environment callEnvironment, Set<String> operationHeaderRoles);

    /**
     * Gets whether the given set of mapped roles provides a caller with the privilege to run as the given
     * "{@code runAsRole}".
     * @param mappedRoles a set of roles obtained from a call to one of this mapper's {@code mapRoles} methods
     * @param runAsRole the role the caller wishes to run as
     * @return {@code true} if running as {@code runAsRole} is allowed
     * @throws UnknowRoleException when the {@code runAsRole} isn't defined.
     */
    boolean canRunAs(Set<String> mappedRoles, String runAsRole);

}
