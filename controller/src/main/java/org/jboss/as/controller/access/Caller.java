/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.access;

import java.security.Permission;
import java.security.Principal;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.security.auth.Subject;

import org.jboss.as.controller.security.ControllerPermission;
import org.jboss.as.core.security.api.RealmPrincipal;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Represents the caller in an access control decision.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @deprecated Replaced by the Elytron {@link SecurityIdentity}
 */
@Deprecated
public final class Caller {

    private static final String UNDEFINED = "UNDEFINED";

    private final SecurityIdentity securityIdentity;

    private volatile String name;
    private volatile String realm = UNDEFINED;
    private volatile Set<String> groups;
    private volatile Set<String> roles;

    private Caller(final SecurityIdentity securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    public static Caller createCaller(final SecurityIdentity securityIdentity) {
        checkPermission(ControllerPermission.CREATE_CALLER);

        return new Caller(securityIdentity);
    }

    /**
     * Obtain the name of the caller, most likely a user but could also be a remote process.
     *
     * @return The name of the caller.
     */
    public String getName() {
        if (name == null && securityIdentity != null) {
            name = securityIdentity.getPrincipal().getName();
        }

        return name;
    }

    /**
     * Obtain the realm used for authentication.
     *
     * This realm name applies to both the user and the groups.
     *
     * @return The name of the realm used for authentication.
     */
    public String getRealm() {
        if (UNDEFINED.equals(realm)) {
            Principal principal = securityIdentity.getPrincipal();
            String realm = null;
            if (principal instanceof RealmPrincipal) {
                realm = ((RealmPrincipal)principal).getRealm();
            }
            this.realm = realm;

        }

        return this.realm;
    }

    /**
     * This method returns a {@link Set} of groups loaded for the user during the authentication step.
     *
     * Note: Groups are also assumed to be specific to the realm.
     *
     * @return The {@link Set} of groups loaded during authentication or an empty {@link Set} if none were loaded.
     */
    public Set<String> getAssociatedGroups() {
        if (groups == null) {
            if (securityIdentity != null) {
                groups = StreamSupport.stream(securityIdentity.getRoles().spliterator(), true).collect(Collectors.toSet());
            } else {
                this.groups = Collections.emptySet();
            }
        }

        return groups;
    }

    /**
     * This method returns the set of roles already associated with the caller.
     *
     * Note: This is the realm mapping of roles and does not automatically mean that these roles will be used for management
     * access control decisions.
     *
     * @return The {@link Set} of associated roles or an empty set if none.
     */
    public Set<String> getAssociatedRoles() {
        return getAssociatedGroups();
    }

    /**
     * Check if this {@link Caller} has a {@link Subject} without needing to access it.
     *
     * This method will always return {@code false} as this is now backed by a {@link SecurityIdentity} this method remains
     * however for binary compatibility.
     *
     * @return true if this {@link Caller} has a {@link Subject}
     */
    public boolean hasSubject() {
        return false;
    }

    /**
     * Obtain the {@link Subject} used to create this caller.
     *
     * This method will always return {@code null} as this is now backed by a {@link SecurityIdentity} this method remains
     * however for binary compatibility.
     *
     * @return The {@link Subject} used to create this caller.
     */
    public Subject getSubject() {
        checkPermission(ControllerPermission.GET_CALLER_SUBJECT);

        return null;
    }

    /**
     * Check if this {@link Caller} has a {@link SecurityIdentity} without needing to access it.
     *
     * @return {@code true} if this {@link Caller} has a {@link SecurityIdentity}.
     */
    public boolean hasSecurityIdentity() {
        return securityIdentity != null;
    }

    /**
     * Obtain the {@link SecurityIdentity} to create this {@link Caller}.
     *
     * @return the {@link SecurityIdentity} to create this {@link Caller}.
     */
    public SecurityIdentity getSecurityIdentity() {
        checkPermission(ControllerPermission.GET_CALLER_SECURITY_IDENTITY);

        return securityIdentity;
    }

    private static void checkPermission(final Permission permission) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(permission);
        }
    }

}
