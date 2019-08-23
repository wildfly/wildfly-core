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

package org.jboss.as.domain.management.security;

import static org.jboss.as.domain.management.RealmConfigurationConstants.VERIFY_PASSWORD_CALLBACK_SUPPORTED;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;
import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Function;

import javax.naming.CommunicationException;
import javax.naming.NamingException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

import org.jboss.as.core.security.RealmUser;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionManager;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.security.LdapSearcherCache.AttachmentKey;
import org.jboss.as.domain.management.security.LdapSearcherCache.SearchResult;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.callback.EvidenceVerifyCallback;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * A CallbackHandler for users within an LDAP directory.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com>Richard Opalka</a>
 */
public class UserLdapCallbackHandler implements Service, CallbackHandlerService {

    private static final AttachmentKey<PasswordCredential> PASSWORD_KEY = AttachmentKey.create(PasswordCredential.class);

    private static final String SERVICE_SUFFIX = "ldap";

    public static final String DEFAULT_USER_DN = "dn";

    private final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer;
    private final Supplier<LdapConnectionManager> connectionManagerSupplier;
    private final Supplier<LdapSearcherCache<LdapEntry, String>> userSearcherSupplier;

    private final boolean allowEmptyPassword;
    private final boolean shareConnection;
    protected final int searchTimeLimit = 10000; // TODO - Maybe make configurable.

    UserLdapCallbackHandler(final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer,
                            final Supplier<LdapConnectionManager> connectionManagerSupplier,
                            final Supplier<LdapSearcherCache<LdapEntry, String>> userSearcherSupplier,
                            boolean allowEmptyPassword, boolean shareConnection) {
        this.callbackHandlerServiceConsumer = callbackHandlerServiceConsumer;
        this.connectionManagerSupplier = connectionManagerSupplier;
        this.userSearcherSupplier = userSearcherSupplier;
        this.allowEmptyPassword = allowEmptyPassword;
        this.shareConnection = shareConnection;
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
        return AuthMechanism.PLAIN;
    }

    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.emptySet();
    }

    public Map<String, String> getConfigurationOptions() {
        return Collections.singletonMap(VERIFY_PASSWORD_CALLBACK_SUPPORTED, Boolean.TRUE.toString());
    }


    @Override
    public boolean isReadyForHttpChallenge() {
        // Configured for LDAP so assume we have some users.
        return true;
    }

    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        return new LdapCallbackHandler(sharedState);
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    /*
     *  Service Methods
     */

    @Override
    public Function<Principal, Principal> getPrincipalMapper() {
        return p -> {
            final ClassLoader old = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(UserLdapCallbackHandler.class);
            LdapConnectionHandler ldapConnectionHandler = createLdapConnectionHandler();
            try {
                try {
                    SearchResult<LdapEntry> searchResult = userSearcherSupplier.get().search(ldapConnectionHandler, p.getName());

                    return p instanceof RealmUser ? new MappedPrincipal(((RealmUser) p).getRealm(), searchResult.getResult().getSimpleName(), p.getName())
                            : new MappedPrincipal(searchResult.getResult().getSimpleName(), p.getName());
                } catch (IllegalStateException | IOException | NamingException e) {
                    SECURITY_LOGGER.trace("Unable to map principal.", e);
                    return p;
                }
            } finally {
                safeClose(ldapConnectionHandler);
                WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(old);
            }
        };
    }

    private LdapConnectionHandler createLdapConnectionHandler() {
        LdapConnectionManager connectionManager = this.connectionManagerSupplier.get();

        return LdapConnectionHandler.newInstance(connectionManager);
    }

    /*
     *  CallbackHandler Method
     */

    private class LdapCallbackHandler implements CallbackHandler {

        private final Map<String, Object> sharedState;

        private LdapCallbackHandler(final Map<String, Object> sharedState) {
            this.sharedState = sharedState;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            if (callbacks.length == 1 && callbacks[0] instanceof AuthorizeCallback) {
                AuthorizeCallback acb = (AuthorizeCallback) callbacks[0];
                String authenticationId = acb.getAuthenticationID();
                String authorizationId = acb.getAuthorizationID();
                boolean authorized = authenticationId.equals(authorizationId);
                if (authorized == false) {
                    SECURITY_LOGGER.tracef(
                            "Checking 'AuthorizeCallback', authorized=false, authenticationID=%s, authorizationID=%s.",
                            authenticationId, authorizationId);
                }
                acb.setAuthorized(authorized);

                return;
            }


            EvidenceVerifyCallback evidenceVerifyCallback = null;
            String username = null;

            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    username = ((NameCallback) current).getDefaultName();
                } else if (current instanceof RealmCallback) {
                    // TODO - Nothing at the moment
                } else if (current instanceof EvidenceVerifyCallback) {
                    evidenceVerifyCallback = (EvidenceVerifyCallback) current;
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }

            if (username == null || username.length() == 0) {
                SECURITY_LOGGER.trace("No username or 0 length username supplied.");
                throw DomainManagementLogger.ROOT_LOGGER.noUsername();
            }
            if (evidenceVerifyCallback == null || evidenceVerifyCallback.getEvidence() == null) {
                SECURITY_LOGGER.trace("No password to verify.");
                throw DomainManagementLogger.ROOT_LOGGER.noPassword();
            }

            final String password;

            if (evidenceVerifyCallback.getEvidence() instanceof PasswordGuessEvidence) {
                 char[] guess = ((PasswordGuessEvidence) evidenceVerifyCallback.getEvidence()).getGuess();
                 password = guess != null ? new String(guess) : null;
            } else {
                password = null;
            }

            if (password == null || (allowEmptyPassword == false && password.length() == 0)) {
                SECURITY_LOGGER.trace("No password or 0 length password supplied.");
                throw DomainManagementLogger.ROOT_LOGGER.noPassword();
            }


            LdapConnectionHandler lch = createLdapConnectionHandler();
            try {
                // 2 - Search to identify the DN of the user connecting
                SearchResult<LdapEntry> searchResult = userSearcherSupplier.get().search(lch, username);

                evidenceVerifyCallback.setVerified(verifyPassword(lch, searchResult, username, password, sharedState));
            } catch (Exception e) {
                SECURITY_LOGGER.trace("Unable to verify identity.", e);
                throw DomainManagementLogger.ROOT_LOGGER.cannotPerformVerification(e);
            } finally {
                if (shareConnection && lch != null && evidenceVerifyCallback != null && evidenceVerifyCallback.isVerified()) {
                    sharedState.put(LdapConnectionHandler.class.getName(), lch);
                } else {
                    lch.close();
                }
            }
        }
    }

    private static boolean verifyPassword(LdapConnectionHandler ldapConnectionHandler, SearchResult<LdapEntry> searchResult, String username, String password, Map<String, Object> sharedState) {
        LdapEntry ldapEntry = searchResult.getResult();

        // 3 - Connect as user once their DN is identified
        final PasswordCredential cachedCredential = searchResult.getAttachment(PASSWORD_KEY);
        if (cachedCredential != null) {
            if (cachedCredential.verify(password)) {
                SECURITY_LOGGER.tracef("Password verified for user '%s' (using cached password)", username);

                sharedState.put(LdapEntry.class.getName(), ldapEntry);
                if (username.equals(ldapEntry.getSimpleName()) == false) {
                    sharedState.put(SecurityRealmService.LOADED_USERNAME_KEY, ldapEntry.getSimpleName());
                }
                return true;
            } else {
                SECURITY_LOGGER.tracef("Password verification failed for user (using cached password) '%s'", username);
                return false;
            }
        } else {
            final ClassLoader old = WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(UserLdapCallbackHandler.class);
            try {
                LdapConnectionHandler verificationHandler = ldapConnectionHandler;
                URI referralUri = ldapEntry.getReferralUri();
                if (referralUri != null) {
                    verificationHandler = verificationHandler.findForReferral(referralUri);
                }

                if (verificationHandler != null) {
                    verificationHandler.verifyIdentity(ldapEntry.getDistinguishedName(), password);
                    SECURITY_LOGGER.tracef("Password verified for user '%s' (using connection attempt)", username);

                    searchResult.attach(PASSWORD_KEY, new PasswordCredential(password));
                    sharedState.put(LdapEntry.class.getName(), ldapEntry);
                    if (username.equals(ldapEntry.getSimpleName()) == false) {
                        sharedState.put(SecurityRealmService.LOADED_USERNAME_KEY, ldapEntry.getSimpleName());
                    }
                    return true;
                } else {
                    SECURITY_LOGGER.tracef(
                            "Password verification failed for user '%s', no connection for referral '%s'", username,
                            referralUri.toString());
                    return false;
                }
            } catch (Exception e) {
                SECURITY_LOGGER.tracef("Password verification failed for user (using connection attempt) '%s'",
                        username);
                return false;
            } finally {
                WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(old);
            }
        }
    }

    private void safeClose(LdapConnectionHandler ldapConnectionHandler) {
        try {
            if (ldapConnectionHandler != null) {
                ldapConnectionHandler.close();
            }
        } catch (IOException e) {
            SECURITY_LOGGER.trace("Unable to close ldapConnectionHandler", e);
        }
    }

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            final String name = principal instanceof MappedPrincipal ? ((MappedPrincipal)principal).getOriginalName() : principal.getName();
            if (name.length() == 0) {
                return RealmIdentity.NON_EXISTENT;
            }

            LdapConnectionHandler ldapConnectionHandler = createLdapConnectionHandler();

            try {
                SearchResult<LdapEntry> searchResult = userSearcherSupplier.get().search(ldapConnectionHandler, name);

                return new RealmIdentityImpl(new NamePrincipal(name), ldapConnectionHandler, searchResult, SecurityRealmService.SharedStateSecurityRealm.getSharedState());
            } catch (IOException | CommunicationException e) {
                safeClose(ldapConnectionHandler);
                throw new RealmUnavailableException(e);
            } catch (IllegalStateException | NamingException e) {
                safeClose(ldapConnectionHandler);
                return RealmIdentity.NON_EXISTENT;
            }
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
            return SupportLevel.UNSUPPORTED;
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
            return SupportLevel.UNSUPPORTED;
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
            checkNotNullParam("evidenceType", evidenceType);
            return PasswordGuessEvidence.class.isAssignableFrom(evidenceType) ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
        }

        private class RealmIdentityImpl implements RealmIdentity {

            private final Principal principal;
            private final LdapConnectionHandler ldapConnectionHandler;
            private final SearchResult<LdapEntry> searchResult;
            private final Map<String, Object> sharedState;

            private RealmIdentityImpl(final Principal principal, final LdapConnectionHandler ldapConnectionHandler, final SearchResult<LdapEntry> searchResult, final Map<String, Object> sharedState) {
                this.principal = principal;
                this.ldapConnectionHandler = ldapConnectionHandler;
                this.searchResult = searchResult;
                this.sharedState = sharedState != null ? sharedState : new HashMap<>();
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName);
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName, parameterSpec);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return null;
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)throws RealmUnavailableException {
                return SecurityRealmImpl.this.getEvidenceVerifySupport(evidenceType, algorithmName);
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {
                if (evidence instanceof PasswordGuessEvidence) {
                    PasswordGuessEvidence passwordGuessEvidence = (PasswordGuessEvidence) evidence;
                    char[] guess =passwordGuessEvidence.getGuess();

                    if (guess == null || (allowEmptyPassword == false && guess.length == 0)) {
                        SECURITY_LOGGER.trace("No password or 0 length password supplied.");
                        return false;
                    }

                    boolean result = verifyPassword(ldapConnectionHandler, searchResult, principal.getName(), new String(guess), sharedState);
                    if (shareConnection && result) {
                        sharedState.put(LdapConnectionHandler.class.getName(), ldapConnectionHandler);
                    }
                    return result;
                }
                return false;
            }

            @Override
            public boolean exists() throws RealmUnavailableException {
                return true;
            }

            @Override
            public void dispose() {
                safeClose(ldapConnectionHandler);
            }

        }
    }

    static class MappedPrincipal extends RealmUser {

        private final String originalName;

        MappedPrincipal(final String name, final String originalName) {
            super(checkNotNullParam("name", name));
            this.originalName = checkNotNullParam("originalName", originalName);
        }

        MappedPrincipal(final String realm, final String name, final String originalName) {
            super(realm, checkNotNullParam("name", name));
            this.originalName = checkNotNullParam("originalName", originalName);
        }

        public String getOriginalName() {
            return originalName;
        }

        public boolean equals(final Object obj) {
            return obj instanceof MappedPrincipal && equals((MappedPrincipal) obj);
        }

        public boolean equals(final MappedPrincipal obj) {
            return obj != null && getName().equals(obj.getName());
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }

    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealm.ServiceUtil.createServiceName(realmName).append(SERVICE_SUFFIX);
        }

    }

    private static final class PasswordCredential {

        private final String password;

        private PasswordCredential(final String password) {
            this.password = password;
        }

        private boolean verify(final String password) {
            return this.password.equals(password);
        }
    }

}
