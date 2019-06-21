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

import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;
import static org.jboss.as.domain.management.security.SecurityRealmService.LOADED_USERNAME_KEY;

import java.io.IOException;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;

import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

/**
 * A CallbackHandler for Kerberos authentication. Currently no Callbacks are supported but later this may be expanded for
 * additional verification.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class KerberosCallbackHandler implements Service, CallbackHandlerService {

    private static final String SERVICE_SUFFIX = "kerberos";
    private final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer;
    private final boolean removeRealm;

    KerberosCallbackHandler(final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer, final boolean removeRealm) {
        this.callbackHandlerServiceConsumer = callbackHandlerServiceConsumer;
        this.removeRealm = removeRealm;
    }

    public void start(final StartContext context) {
        callbackHandlerServiceConsumer.accept(this);
    }

    public void stop(final StopContext context) {
        callbackHandlerServiceConsumer.accept(null);
    }

    /*
     * CallbackHandlerService Methods
     */

    public AuthMechanism getPreferredMechanism() {
        return AuthMechanism.KERBEROS;
    }

    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.emptySet();
    }

    public Map<String, String> getConfigurationOptions() {
        return Collections.emptyMap();
    }

    public boolean isReady() {
        return true;
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        // Kerberos so if configured it is ready.
        return true;
    }

    public CallbackHandler getCallbackHandler(final Map<String, Object> sharedState) {
        return new CallbackHandler() {

            @Override
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback current : callbacks) {
                    if (current instanceof AuthorizeCallback) {
                        AuthorizeCallback acb = (AuthorizeCallback) current;
                        boolean authorized = acb.getAuthenticationID().equals(acb.getAuthorizationID());
                        if (authorized) {
                            String userName = acb.getAuthenticationID();
                            int atIndex = acb.getAuthenticationID().indexOf('@');
                            if (removeRealm && atIndex > 0) {
                                sharedState.put(LOADED_USERNAME_KEY, userName.substring(0, atIndex));
                            }
                        } else {
                            SECURITY_LOGGER.tracef(
                                    "Checking 'AuthorizeCallback', authorized=false, authenticationID=%s, authorizationID=%s.",
                                    acb.getAuthenticationID(), acb.getAuthorizationID());
                        }
                        acb.setAuthorized(authorized);
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }
            }
        };
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new KerberosSecurityRealm();
    }

    @Override
    public Function<Principal, Principal> getPrincipalMapper() {
        if (removeRealm) {
            return p -> {
                int atIndex = p.getName().indexOf('@');
                if (atIndex > 0) {
                    return new NamePrincipal(p.getName().substring(0, atIndex));
                }
                return p;
            };
        }
        return CallbackHandlerService.super.getPrincipalMapper();
    }

    private class KerberosSecurityRealm implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            return new KerberosRealmIdentity(principal);
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

        private class KerberosRealmIdentity implements RealmIdentity {

            private final Principal principal;

            KerberosRealmIdentity(final Principal principal) {
                this.principal = principal;
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                    throws RealmUnavailableException {
                return KerberosSecurityRealm.this.getCredentialAcquireSupport(credentialType, algorithmName);
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec)
                    throws RealmUnavailableException {
                return KerberosSecurityRealm.this.getCredentialAcquireSupport(credentialType, algorithmName, parameterSpec);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return null;
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                    throws RealmUnavailableException {
                return KerberosSecurityRealm.this.getEvidenceVerifySupport(evidenceType, algorithmName);
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

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealm.ServiceUtil.createServiceName(realmName).append(SERVICE_SUFFIX);
        }
    }
}
