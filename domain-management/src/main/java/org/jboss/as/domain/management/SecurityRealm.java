/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management;

import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.security.auth.Subject;
import org.jboss.as.domain.management.security.SecurityRealmResourceDefinition;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;

/**
 * Interface to the security realm.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface SecurityRealm {

    /**
     * @return The name of this SecurityRealm
     */
    String getName();

    /**
     * @return The set of authentication mechanisms supported by this realm.
     */
    Set<AuthMechanism> getSupportedAuthenticationMechanisms();

    /**
     * @return A Map containing the combined configuration options for the specified mechanisms.
     */
    Map<String, String> getMechanismConfig(final AuthMechanism mechanism);

    /**
     * @param mechanism - The mechanism being used for authentication.
     * @return The {@link AuthorizingCallbackHandler} for the specified mechanism.
     * @throws IllegalArgumentException If the mechanism is not supported by this realm.
     */
    AuthorizingCallbackHandler getAuthorizingCallbackHandler(final AuthMechanism mechanism);

    /**
     * Indicate of this security realm is ready for challenge/response style HTTP authentication.
     *
     * @return true if the security realm is ready for challenge/response style HTTP authentication.
     */
    boolean isReadyForHttpChallenge();

    /**
     * Used to obtain the SSLContext as configured for this security realm.
     *
     * @return the SSLContext server identity for this realm.
     */
    SSLContext getSSLContext();

    /**
     * @return A CallbackHandlerFactory for a pre-configured secret.
     */
    CallbackHandlerFactory getSecretCallbackHandlerFactory();

    /**
     * Used to obtain a {@link SubjectIdentity} containing a {@link Subject} for the Kerberos server identity represented by this {@link SecurityRealm}.
     *
     * This method can return {@code null} if there is no appropriate identity.
     *
     * @param protocol The protocol in use.
     * @param forHost The name of the host this identity should represent.
     * @return A {@link SubjectIdentity} for the server identity or {@code null} if none are available.
     */
    SubjectIdentity getSubjectIdentity(final String protocol, final String forHost);

    /**
     * Get a {@link SaslAuthenticationFactory} backed by this {@link SecurityRealm}.
     *
     * @return a {@link SaslAuthenticationFactory} backed by this {@link SecurityRealm}.
     */
    SaslAuthenticationFactory getSaslAuthenticationFactory();

    /**
     * Get a {@link HttpAuthenticationFactory} backed by this {@link SecurityRealm}.
     *
     * @return a {@link HttpAuthenticationFactory} backed by this {@link SecurityRealm}.
     */
    HttpAuthenticationFactory getHttpAuthenticationFactory();

    public static final class ServiceUtil {

        private static final ServiceName BASE_SERVICE_NAME = ServiceName.JBOSS.append("server", "controller", "management", "security_realm");

        private ServiceUtil() {
        }

        public static ServiceName createLegacyServiceName(final String realmName) {
            return BASE_SERVICE_NAME.append(realmName);
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealmResourceDefinition.MANAGEMENT_SECURITY_REALM_CAPABILITY.getCapabilityServiceName(realmName);
        }

        @Deprecated
        public static ServiceBuilder<?> addDependency(ServiceBuilder<?> sb, Injector<SecurityRealm> injector,
                String realmName, boolean optional) {
            ServiceBuilder.DependencyType type = optional ? ServiceBuilder.DependencyType.OPTIONAL : ServiceBuilder.DependencyType.REQUIRED;
            sb.addDependency(type, createServiceName(realmName), SecurityRealm.class, injector);
            return sb;
        }

        public static ServiceBuilder<?> addDependency(ServiceBuilder<?> sb, Injector<SecurityRealm> injector, String realmName) {
            return sb.addDependency(ServiceBuilder.DependencyType.REQUIRED, createServiceName(realmName), SecurityRealm.class, injector);
        }

    }

}
