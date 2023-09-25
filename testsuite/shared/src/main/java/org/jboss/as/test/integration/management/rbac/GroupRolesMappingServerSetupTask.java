/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.rbac;

import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.addRoleGroup;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.addRoleMapping;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.removeRoleGroup;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.removeRoleMapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * {@link org.wildfly.core.testrunner.ServerSetupTask} that can add group->role mappings to the access=authorization configuration.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class GroupRolesMappingServerSetupTask {
    private final Map<String, Set<String>> rolesToGroups = new HashMap<String, Set<String>>();

    protected GroupRolesMappingServerSetupTask(final Map<String, Set<String>> rolesToGroups) {
        this.rolesToGroups.putAll(rolesToGroups);
    }

    public void setup(ModelControllerClient client) throws IOException {
        for (Map.Entry<String, Set<String>> roleEntry : rolesToGroups.entrySet()) {
            String role = roleEntry.getKey();
            addRoleMapping(role, client);
            for (String entity : roleEntry.getValue()) {
                addRoleGroup(role, entity, client);
            }
        }
    }

    public void tearDown(ModelControllerClient client) throws IOException {
        for (Map.Entry<String, Set<String>> roleEntry : rolesToGroups.entrySet()) {
            String role = roleEntry.getKey();
            for (String entity : roleEntry.getValue()) {
                removeRoleGroup(role, entity, client);
            }
            if (!SUPERUSER_ROLE.equals(role)) {
                removeRoleMapping(role, client);
            }
        }
    }
}
