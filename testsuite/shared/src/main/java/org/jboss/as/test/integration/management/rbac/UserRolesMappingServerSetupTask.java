/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.rbac;

import static org.jboss.as.test.integration.management.rbac.RbacUtil.ADMINISTRATOR_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.ADMINISTRATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.AUDITOR_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.AUDITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.DEPLOYER_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.DEPLOYER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.OPERATOR_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.OPERATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_ROLE;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.addRoleMapping;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.addRoleUser;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.removeRoleMapping;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.removeRoleUser;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.client.ModelControllerClient;

/**
  Task that can add user->role mappings to the access=authorization configuration.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class UserRolesMappingServerSetupTask {

    private final Map<String, Set<String>> rolesToUsers = new HashMap<String, Set<String>>();

    protected UserRolesMappingServerSetupTask(final Map<String, Set<String>> rolesToUsers) {
        this.rolesToUsers.putAll(rolesToUsers);
    }


    public void setup(ModelControllerClient client) throws IOException {
        for (Map.Entry<String, Set<String>> roleEntry : rolesToUsers.entrySet()) {
            String role = roleEntry.getKey();
            addRoleMapping(role, client);
            for (String user : roleEntry.getValue()) {
                addRoleUser(role, user, client);
            }
        }
    }

    public void tearDown(ModelControllerClient client) throws IOException {
        for (Map.Entry<String, Set<String>> roleEntry : rolesToUsers.entrySet()) {
            String role = roleEntry.getKey();
            for (String user : roleEntry.getValue()) {
                removeRoleUser(role, user, client);
            }
            if (!SUPERUSER_USER.equals(role)) {
                removeRoleMapping(role, client);
            }
        }
    }

    /**
     * {@link UserRolesMappingServerSetupTask} that adds a single user mapping for each standard
     * role, with the username the same as the role name.
     */
    public static class StandardUsersSetup extends UserRolesMappingServerSetupTask {

        static {
            Map<String, Set<String>> rolesToUsers = new HashMap<String, Set<String>>();
            rolesToUsers.put(MONITOR_ROLE, Collections.singleton(MONITOR_USER));
            rolesToUsers.put(OPERATOR_ROLE, Collections.singleton(OPERATOR_USER));
            rolesToUsers.put(MAINTAINER_ROLE, Collections.singleton(MAINTAINER_USER));
            rolesToUsers.put(DEPLOYER_ROLE, Collections.singleton(DEPLOYER_USER));
            rolesToUsers.put(ADMINISTRATOR_ROLE, Collections.singleton(ADMINISTRATOR_USER));
            rolesToUsers.put(AUDITOR_ROLE, Collections.singleton(AUDITOR_USER));
            rolesToUsers.put(SUPERUSER_ROLE, Collections.singleton(SUPERUSER_USER));
            STANDARD_USERS = rolesToUsers;
        }

        private static final Map<String, Set<String>> STANDARD_USERS;

        public static final StandardUsersSetup INSTANCE = new StandardUsersSetup();

        public StandardUsersSetup() {
            super(STANDARD_USERS);
        }
    }
}
