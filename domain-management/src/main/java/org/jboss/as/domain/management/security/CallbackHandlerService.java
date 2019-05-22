/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.security;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.auth.server.SecurityRealm;

/**
 * The interface to be implemented by all services supplying callback handlers.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface CallbackHandlerService {

    /**
     * @return The preferred authentication mechanism of the CBH.
     */
    AuthMechanism getPreferredMechanism();

    /**
     * @return A set of additional mechanisms that can be handled by CallbackHandlers supplied by this service.
     */
    Set<AuthMechanism> getSupplementaryMechanisms();

    /**
     * @return The transport independent config options for the CallbackHandler supplied by this service.
     */
    Map<String, String> getConfigurationOptions();

    /**
     * Is this CallbackHandler ready for handling HTTP requests that involve a challenge?
     *
     * To be used by the HTTP interface to display an error if the administrator has not completed the set-up of their
     * installation.
     *
     * @return indication of if this {@link CallbackHandlerService} is ready for challenge based authentication.
     */
    boolean isReadyForHttpChallenge();

    /**
     * Obtain a CallbackHandler instance for use during authentication.
     *
     * The service can decide if it will return a single shared CallbackHandler or a new one for each call to this method.
     *
     * The shared state is authentication request specific but this is the only time it will be provided, for CallbackHandlers
     * making use of this then state specific instances should be returned.
     *
     * @param sharedState - The state to be shared between the authentication side of the call and the authorization side.
     * @return A CallbackHandler instance.
     */
    CallbackHandler getCallbackHandler(final Map<String, Object> sharedState);

    /**
     * Get an Elytron {@link SecurityRealm} that is backed by this callback handler.
     *
     * @return an Elytron {@link SecurityRealm} that is backed by this callback handler.
     */
    SecurityRealm getElytronSecurityRealm();

    /**
     * Get a principal mapper to be used before the realm is selected.
     *
     * @return a principal mapper to be used before the realm is selected.
     */
    default Function<Principal, Principal> getPrincipalMapper() {
        return Function.identity();
    }

    /**
     * Where the Elytron {@link SecurityRealm} is used should group loading also be enabled?
     *
     * @return {@code true} if group loading should be enabled, {@code false} otherwise.
     */
    default boolean allowGroupLoading() {
        return true;
    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static Supplier<CallbackHandlerService> requires(final ServiceBuilder<?> sb, final ServiceName serviceName) {
            return sb.requires(serviceName);
        }
    }

}
