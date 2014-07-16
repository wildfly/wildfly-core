/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.jboss.as.domain.http.server.security.keycloak;

import java.security.Principal;
import java.util.Set;
import javax.security.auth.Subject;
import org.jboss.as.core.security.RealmGroup;
import org.jboss.as.core.security.RealmUser;
import org.jboss.as.domain.http.server.security.SubjectAccount;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.undertow.KeycloakUndertowAccount;

/**
 * Keycloak account that creates a subject to be used by SubjectDoAsHandler.
 *
 * This allows roles and users defined in Keycloak to be used with WildFly's
 * RBAC.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class KeycloakUndertowSubjectAccount extends KeycloakUndertowAccount implements SubjectAccount {

    private final Subject subject;

    public KeycloakUndertowSubjectAccount(KeycloakPrincipal principal, RefreshableKeycloakSecurityContext session, KeycloakDeployment deployment) {
        super(principal, session, deployment);

        this.subject = new Subject();

        String realm = deployment.getRealm();
        Set<Principal> principals = subject.getPrincipals();
        principals.add(new RealmUser(realm, session.getToken().getPreferredUsername()));

        // TODO: I think there is more work to be done here, but this will suffice for now
        // Look at org.keycloak.adapters.wildfly.SecurityInfoHelper to see all
        // that Keycloak supports.
        for (String role : getRoles()) {
            principals.add(new RealmGroup(realm, role));
        }
    }

    @Override
    public Subject getSubject() {
        return this.subject;
    }

}
