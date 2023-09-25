/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.permission;

import java.security.PermissionCollection;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Factory for a compatible set of permissions. Implementations must ensure that the permissions returned
 * by the {@code getUserPermissions} methods are compatible with the permissions returned by the
 * {@code getRequiredPermissions} methods. Compatible means the user permissions can correctly
 * evaluate whether they
 * {@link java.security.Permission#implies(java.security.Permission) imply the required permissions}
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface PermissionFactory {

    PermissionCollection getUserPermissions(SecurityIdentity identity, Environment callEnvironment, Action action, TargetAttribute target);

    PermissionCollection getUserPermissions(SecurityIdentity identity, Environment callEnvironment, Action action, TargetResource target);

    PermissionCollection getRequiredPermissions(Action action, TargetAttribute target);

    PermissionCollection getRequiredPermissions(Action action, TargetResource target);

    PermissionCollection getUserPermissions(SecurityIdentity identity, Environment callEnvironment, JmxAction action, JmxTarget target);

    PermissionCollection getRequiredPermissions(JmxAction action, JmxTarget target);
}
