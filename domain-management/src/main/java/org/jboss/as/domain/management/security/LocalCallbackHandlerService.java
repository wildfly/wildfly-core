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

import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;
import static org.jboss.as.domain.management.RealmConfigurationConstants.LOCAL_DEFAULT_USER;
import static org.jboss.as.domain.management.security.SecurityRealmService.SKIP_GROUP_LOADING_KEY;

import java.io.IOException;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;

import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

/**
 * The Service providing the LocalCallbackHandler implementation.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class LocalCallbackHandlerService implements Service, CallbackHandlerService {

    private static final String SERVICE_SUFFIX = "local";

    private final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer;
    private final String defaultUser;
    private final String allowedUsers;
    private boolean allowAll;
    private final Set<String> allowedUsersSet = new HashSet<String>();
    private final boolean skipGroupLoading;

    LocalCallbackHandlerService(final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer,
                                final String defaultUser, final String allowedUsers, final boolean skipGroupLoading) {
        this.callbackHandlerServiceConsumer = callbackHandlerServiceConsumer;
        this.defaultUser = defaultUser;
        this.allowedUsers = allowedUsers;
        this.skipGroupLoading = skipGroupLoading;
    }

    /*
     * CallbackHandlerService Methods
     */

    public AuthMechanism getPreferredMechanism() {
        return AuthMechanism.LOCAL;
    }

    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.emptySet();
    }

    public Map<String, String> getConfigurationOptions() {
        if (defaultUser != null) {
            return Collections.singletonMap(LOCAL_DEFAULT_USER, defaultUser);
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        // Never used for HTTP authentication.
        return false;
    }

    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        return new LocalCallbackHandler(sharedState);
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new LocalSecurityRealm();
    }

    @Override
    public boolean allowGroupLoading() {
        return !skipGroupLoading;
    }

    private class LocalSecurityRealm implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            String name = principal.getName();
            if (allowAll || allowedUsersSet.contains(name)) {
                return new LocalRealmIdentity(principal);
            }

            return RealmIdentity.NON_EXISTENT;
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                throws RealmUnavailableException {
            return SupportLevel.UNSUPPORTED;
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec)
                throws RealmUnavailableException {
            return SupportLevel.UNSUPPORTED;
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return SupportLevel.UNSUPPORTED;
        }


        private class LocalRealmIdentity implements RealmIdentity {

            private final Principal principal;

            LocalRealmIdentity(final Principal principal) {
                this.principal = principal;
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                    throws RealmUnavailableException {
                return LocalSecurityRealm.this.getCredentialAcquireSupport(credentialType, algorithmName);
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec)
                    throws RealmUnavailableException {
                return LocalSecurityRealm.this.getCredentialAcquireSupport(credentialType, algorithmName, parameterSpec);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return null;
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                    throws RealmUnavailableException {
                return LocalSecurityRealm.this.getEvidenceVerifySupport(evidenceType, algorithmName);
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {
                return false;
            }

            @Override
            public boolean exists() throws RealmUnavailableException {
                return true;
            }

        }

    }

    public void start(final StartContext context) {
        if (defaultUser != null) {
            allowedUsersSet.add(defaultUser);
        }
        if (allowedUsers != null) {
            if ("*".equals(allowedUsers)) {
                allowAll = true;
            } else {
                String[] users = allowedUsers.split(",");
                for (String current : users) {
                    allowedUsersSet.add(current);
                }
            }
        }
        callbackHandlerServiceConsumer.accept(this);
    }

    public void stop(final StopContext context) {
        callbackHandlerServiceConsumer.accept(null);
        allowAll = false;
        allowedUsersSet.clear(); // Effectively disables this CBH
    }

    /*
     * CallbackHandler Method
     */

    private final class LocalCallbackHandler implements CallbackHandler {

        private final Map<String, Object> sharedState;

        private LocalCallbackHandler(final Map<String, Object> sharedState) {
            this.sharedState = sharedState;
        }

        /**
         * @see javax.security.auth.callback.CallbackHandler#handle(javax.security.auth.callback.Callback[])
         */
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    String userName = ncb.getDefaultName();
                    if ((allowAll || allowedUsersSet.contains(userName)) == false) {
                        SECURITY_LOGGER.tracef("Username '%s' is not permitted for local authentication.", userName);
                        throw DomainManagementLogger.ROOT_LOGGER.invalidLocalUser(userName);
                    }
                } else if (current instanceof AuthorizeCallback) {
                    AuthorizeCallback acb = (AuthorizeCallback) current;
                    boolean authorized = acb.getAuthenticationID().equals(acb.getAuthorizationID());
                    if (authorized == false) {
                        SECURITY_LOGGER.tracef(
                                "Checking 'AuthorizeCallback', authorized=false, authenticationID=%s, authorizationID=%s.",
                                acb.getAuthenticationID(), acb.getAuthorizationID());
                    }
                    acb.setAuthorized(authorized);

                    if (authorized && skipGroupLoading) {
                        sharedState.put(SKIP_GROUP_LOADING_KEY, Boolean.TRUE);
                    }
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }
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
