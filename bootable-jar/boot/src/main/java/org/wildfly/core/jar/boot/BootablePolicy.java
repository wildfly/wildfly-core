/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * This is a direct copy of org.jboss.modules.ModulesPolicy from jboss-modules as the bootstrap of
 * bootable jar is very similar to a jboss-modules bootstrap.
 */

package org.wildfly.core.jar.boot;

import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.Provider;

final class BootablePolicy extends Policy {

    private static final AllPermission ALL_PERMISSION = new AllPermission();

    static final Permissions DEFAULT_PERMISSION_COLLECTION = getAllPermission();

    private static final CodeSource ourCodeSource = BootablePolicy.class.getProtectionDomain().getCodeSource();

    private final Policy policy;

    private static Permissions getAllPermission() {
        final Permissions permissions = new Permissions();
        permissions.add(ALL_PERMISSION);
        return permissions;
    }

    BootablePolicy(final Policy policy) {
        this.policy = policy;
    }

    public Provider getProvider() {
        return policy.getProvider();
    }

    public String getType() {
        return policy.getType();
    }

    public Parameters getParameters() {
        return policy.getParameters();
    }

    public PermissionCollection getPermissions(final CodeSource codesource) {
        return codesource != null && codesource.equals(ourCodeSource) ? getAllPermission() : policy.getPermissions(codesource);
    }

    public PermissionCollection getPermissions(final ProtectionDomain domain) {
        final CodeSource codeSource = domain.getCodeSource();
        return codeSource != null && codeSource.equals(ourCodeSource) ? getAllPermission() : policy.getPermissions(domain);
    }

    public boolean implies(final ProtectionDomain domain, final Permission permission) {
        final CodeSource codeSource = domain.getCodeSource();
        return codeSource != null && codeSource.equals(ourCodeSource) || policy.implies(domain, permission);
    }

    public void refresh() {
        policy.refresh();
    }
}
