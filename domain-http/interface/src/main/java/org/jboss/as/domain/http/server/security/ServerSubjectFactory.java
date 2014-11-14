/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.http.server.security;

import io.undertow.security.api.GSSAPIServerSubjectFactory;

import java.security.GeneralSecurityException;

import javax.security.auth.Subject;

import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.SubjectIdentity;

/**
 * A {@link GSSAPIServerSubjectFactory} that obtaines the {@link SubjectIdentity} from the {@link SecurityRealm} and associates
 * it with the current request so it can be cleaned up as the end of the request.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ServerSubjectFactory implements GSSAPIServerSubjectFactory {

    private static final String HTTP_PROTOCOL = "HTTP";

    private final SecurityRealm securityRealm;
    private final RealmIdentityManager realmIdentityManager;

    public ServerSubjectFactory(final SecurityRealm securityRealm, final RealmIdentityManager realmIdentityManager) {
        this.securityRealm = securityRealm;
        this.realmIdentityManager = realmIdentityManager;
    }

    @Override
    public Subject getSubjectForHost(String hostName) throws GeneralSecurityException {
        SubjectIdentity subjectIdentity = securityRealm.getSubjectIdentity(HTTP_PROTOCOL, hostName);
        if (subjectIdentity != null) {
            realmIdentityManager.setCurrentSubjectIdentity(subjectIdentity);
        }

        return subjectIdentity == null ? null : subjectIdentity.getSubject();
    }

}
