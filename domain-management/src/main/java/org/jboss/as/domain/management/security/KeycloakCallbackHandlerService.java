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

package org.jboss.as.domain.management.security;


import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 *
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
class KeycloakCallbackHandlerService implements Service<CallbackHandlerService>, CallbackHandlerService {

    private static final String SERVICE_SUFFIX = "keycloak";

    KeycloakCallbackHandlerService() {
    }

    /*
     * CallbackHandlerService Methods
     */
    @Override
    public AuthMechanism getPreferredMechanism() {
        System.out.println(">>>>>> KeycloakCallbackHandlerService.getPreferredMechanism called");
        return AuthMechanism.KEYCLOAK;
    }

    @Override
    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, String> getConfigurationOptions() {
        System.out.println(">>>>>> KeycloakCallbackHandlerService.getConfigurationOptions called");
        return Collections.emptyMap();
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        System.out.println(">>>>>> KeycloakCallbackHandlerService.getCallbackHandler called");
        return new KeycloakCallbackHander(sharedState);
    }

    /*
     * Service Methods
     */

    @Override
    public CallbackHandlerService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public void start(StartContext context) throws StartException {
        System.out.println(">>>>>> Keycloak service started");
    }

    public void stop(StopContext context) {
    }

    /*
     * CallbackHandler Method
     */

    private final class KeycloakCallbackHander implements CallbackHandler {

        private final Map<String, Object> sharedState;

        private KeycloakCallbackHander(final Map<String, Object> sharedState) {
            this.sharedState = sharedState;
            System.out.println(">>>>>> KeycloakCallbackHandler shared state=" + sharedState);
        }

        /**
         * @see javax.security.auth.callback.CallbackHandler#handle(javax.security.auth.callback.Callback[])
         */
        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            System.out.println(">>>>>> KeycloakCallbackHander.handle() callbacks=" + callbacks);
        }
    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealm.ServiceUtil.createServiceName(realmName).append(SERVICE_SUFFIX);
        }

    }

}
