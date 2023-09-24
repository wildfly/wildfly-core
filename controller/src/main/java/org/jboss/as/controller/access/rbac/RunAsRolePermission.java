/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access.rbac;

import java.security.BasicPermission;

/**
 * A simple {@link Permission} to allow code being executed without an associated remote to be granted the permission to execute
 * using a specified role.
 *
 * Initially by default only one role is used for in-vm calls, however this could be extended to allow different in-vm calls to
 * be granted different roles.
 *
 * Where a {@link Subject} representing a remote user is already combined with the {@link AccessControlContext} this
 * {@link SecurityManager} check is not used.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class RunAsRolePermission extends BasicPermission {

    private static final long serialVersionUID = 640368502336382562L;

    public RunAsRolePermission(final String roleName) {
        super(RunAsRolePermission.class.getName() + "." + roleName);
    }

}
