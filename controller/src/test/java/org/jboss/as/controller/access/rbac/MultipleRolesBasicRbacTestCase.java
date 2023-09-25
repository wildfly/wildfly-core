/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;

import org.jboss.dmr.ModelNode;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class MultipleRolesBasicRbacTestCase extends BasicRbacTestCase {
    @Override
    protected ModelNode executeWithRole(ModelNode operation, StandardRole role) {
        if (role == StandardRole.MONITOR) {
            return super.executeWithRole(operation, role);
        }
        // Just add "monitor", as that won't widen the permissions

        operation.get(OPERATION_HEADERS, "roles").add(StandardRole.MONITOR.name()).add(role.name());
        return getController().execute(operation, null, null, null);
    }
}
