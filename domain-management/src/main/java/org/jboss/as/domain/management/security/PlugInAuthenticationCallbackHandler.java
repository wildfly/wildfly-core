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

import static org.jboss.as.domain.management.RealmConfigurationConstants.DIGEST_PLAIN_TEXT;
import static org.jboss.as.domain.management.RealmConfigurationConstants.VERIFY_PASSWORD_CALLBACK_SUPPORTED;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;
import static org.wildfly.security.password.interfaces.ClearPassword.ALGORITHM_CLEAR;
import static org.wildfly.security.password.interfaces.DigestPassword.ALGORITHM_DIGEST_MD5;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.plugin.AuthenticationPlugIn;
import org.jboss.as.domain.management.plugin.Credential;
import org.jboss.as.domain.management.plugin.DigestCredential;
import org.jboss.as.domain.management.plugin.Identity;
import org.jboss.as.domain.management.plugin.PasswordCredential;
import org.jboss.as.domain.management.plugin.PlugInConfigurationSupport;
import org.jboss.as.domain.management.plugin.ValidatePasswordCredential;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.Assert;
import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.callback.EvidenceVerifyCallback;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.DigestPasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;


/**
 * CallbackHandlerService to integrate the plug-ins.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class PlugInAuthenticationCallbackHandler extends AbstractPlugInService implements Service,
        CallbackHandlerService {

    private static final String SERVICE_SUFFIX = "plug-in-authentication";

    private static UsernamePasswordHashUtil hashUtil = null;
    private final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer;
    private final AuthMechanism mechanism;

    PlugInAuthenticationCallbackHandler(final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer,
                                        final Supplier<PlugInLoaderService> plugInLoaderSupplier,
                                        final String realmName,
                                        final String pluginName,
                                        final Map<String, String> properties,
                                        final AuthMechanism mechanism) {
        super(plugInLoaderSupplier, realmName, pluginName, properties);
        this.callbackHandlerServiceConsumer = callbackHandlerServiceConsumer;
        this.mechanism = mechanism;
    }

    @Override
    public void start(final StartContext context) {
        callbackHandlerServiceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        callbackHandlerServiceConsumer.accept(null);
    }

    private static UsernamePasswordHashUtil getHashUtil() {
        if (hashUtil == null) {
            try {
                hashUtil = new UsernamePasswordHashUtil();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return hashUtil;
    }

    /*
     * CallbackHandlerService Methods
     */

    public AuthMechanism getPreferredMechanism() {
        return mechanism;
    }

    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.emptySet();
    }

    public Map<String, String> getConfigurationOptions() {
        if (mechanism == AuthMechanism.DIGEST) {
            // If the plug-in returns a plain text password we can hash it.
            return Collections.singletonMap(DIGEST_PLAIN_TEXT, Boolean.FALSE.toString());
        } else {
            return Collections.singletonMap(VERIFY_PASSWORD_CALLBACK_SUPPORTED, Boolean.TRUE.toString());
        }
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        // Assume we are ready.
        return true;
    }

    public CallbackHandler getCallbackHandler(final Map<String, Object> sharedState) {
        final String name = getPlugInName();
        final AuthenticationPlugIn<Credential> ap = getPlugInLoader().loadAuthenticationPlugIn(name);
        if (ap instanceof PlugInConfigurationSupport) {
            PlugInConfigurationSupport pcf = (PlugInConfigurationSupport) ap;
            try {
                pcf.init(getConfiguration(), sharedState);
            } catch (IOException e) {
                throw DomainManagementLogger.ROOT_LOGGER.unableToInitialisePlugIn(name, e.getMessage());
            }
        }

        return new CallbackHandler() {

            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

                final String realmName = getRealmName();

                List<Callback> toRespondTo = new LinkedList<Callback>();

                String userName = null;
                Credential credential = null;

                // A single pass may be sufficient but by using a two pass approach the Callbackhandler will not
                // fail if an unexpected order is encountered.

                // First Pass - is to double check no unsupported callbacks and to retrieve
                // information from the callbacks passing in information.
                for (Callback current : callbacks) {
                    if (current instanceof AuthorizeCallback) {
                        toRespondTo.add(current);
                    } else if (current instanceof NameCallback) {
                        NameCallback nameCallback = (NameCallback) current;
                        userName = nameCallback.getDefaultName();
                        Identity identity = ap.loadIdentity(userName, realmName);
                        if (identity != null) {
                            credential = identity.getCredential();
                        }
                    } else if (current instanceof PasswordCallback) {
                        toRespondTo.add(current);
                    } else if (current instanceof CredentialCallback) {
                        toRespondTo.add(current);
                    } else if (current instanceof EvidenceVerifyCallback) {
                        toRespondTo.add(current);
                    } else if (current instanceof RealmCallback) {
                        String realm = ((RealmCallback) current).getDefaultText();
                        if (realmName.equals(realm) == false) {
                            throw DomainManagementLogger.ROOT_LOGGER.invalidRealm(realm, realmName);
                        }
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }

                // Second Pass - Now iterate the Callback(s) requiring a response.
                for (Callback current : toRespondTo) {
                    if (current instanceof AuthorizeCallback) {
                        AuthorizeCallback acb = (AuthorizeCallback) current;
                        boolean authorized = acb.getAuthenticationID().equals(acb.getAuthorizationID());
                        if (authorized == false) {
                            SECURITY_LOGGER.tracef(
                                    "Checking 'AuthorizeCallback', authorized=false, authenticationID=%s, authorizationID=%s.",
                                    acb.getAuthenticationID(), acb.getAuthorizationID());
                        }
                        acb.setAuthorized(authorized);
                    } else if (current instanceof PasswordCallback) {
                        if (credential == null) {
                            SECURITY_LOGGER.tracef("User '%s' not found.", userName);
                            throw new UserNotFoundException(userName);
                        }

                        if (credential instanceof PasswordCredential) {
                            ((PasswordCallback) current).setPassword(((PasswordCredential) credential).getPassword());
                        } else {
                            throw new UnsupportedCallbackException(current);
                        }
                    } else if (current instanceof CredentialCallback) {
                        if (credential == null) {
                            SECURITY_LOGGER.tracef("User '%s' not found.", userName);
                            throw new UserNotFoundException(userName);
                        }

                        CredentialCallback credentialCallback = (CredentialCallback) current;
                        if (org.wildfly.security.credential.PasswordCredential.class.isAssignableFrom(credentialCallback.getCredentialType()) == false) {
                            throw new UnsupportedCallbackException(current);
                        }

                        String algorithm = credentialCallback.getAlgorithm();
                        final PasswordFactory passwordFactory;
                        final PasswordSpec passwordSpec;
                        if (credential instanceof DigestCredential && (algorithm == null || ALGORITHM_DIGEST_MD5.equals(algorithm))) {
                            passwordFactory = getPasswordFactory(ALGORITHM_DIGEST_MD5);
                            byte[] hashed = ByteIterator.ofBytes(((DigestCredential) credential).getHash().getBytes(StandardCharsets.UTF_8)).asUtf8String().hexDecode().drain();
                            passwordSpec = new DigestPasswordSpec(userName, realmName, hashed);
                        } else if (credential instanceof PasswordCredential) {
                            if (algorithm == null || ALGORITHM_CLEAR.equals(algorithm)) {
                                passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                                passwordSpec = new ClearPasswordSpec(((PasswordCredential) credential).getPassword());
                            } else if (ALGORITHM_DIGEST_MD5.equals(algorithm)) {
                                passwordFactory = getPasswordFactory(ALGORITHM_DIGEST_MD5);
                                UsernamePasswordHashUtil hashUtil = getHashUtil();
                                synchronized (hashUtil) {
                                    byte[] hashed = hashUtil
                                            .generateHashedHexURP(userName, realmName,
                                                    ((PasswordCredential) credential).getPassword()).getBytes(StandardCharsets.UTF_8);
                                    passwordSpec = new DigestPasswordSpec(userName, realmName, hashed);
                                }
                            } else {
                                throw new UnsupportedCallbackException(current);
                            }
                        } else {
                            throw new UnsupportedCallbackException(current);
                        }

                        try {
                            credentialCallback.setCredential(credentialCallback.getCredentialType()
                                    .cast(new org.wildfly.security.credential.PasswordCredential(
                                            passwordFactory.generatePassword(passwordSpec))));
                        } catch (InvalidKeySpecException e) {
                            throw new IllegalStateException(e);
                        }
                    } else if (current instanceof EvidenceVerifyCallback) {
                        if (credential == null) {
                            SECURITY_LOGGER.tracef("User '%s' not found.", userName);
                            throw new UserNotFoundException(userName);
                        }

                        EvidenceVerifyCallback evc = (EvidenceVerifyCallback) current;
                        PasswordGuessEvidence evidence = (PasswordGuessEvidence) evc.getEvidence();
                        char[] guess = evidence.getGuess();

                        if (credential instanceof PasswordCredential) {
                            boolean verified = Arrays.equals(((PasswordCredential) credential).getPassword(), guess);
                            if (verified == false) {
                                SECURITY_LOGGER.tracef("Password verification failed for user '%s'", userName);
                            }
                            evc.setVerified(verified);
                        } else if (credential instanceof DigestCredential) {
                            UsernamePasswordHashUtil hashUtil = getHashUtil();
                            String hash;
                            synchronized (hashUtil) {
                                hash = hashUtil.generateHashedHexURP(userName, realmName, guess);
                            }
                            String expected = ((DigestCredential) credential).getHash();
                            boolean verified = expected.equals(hash);
                            if (verified == false) {
                                SECURITY_LOGGER.tracef("Digest verification failed for user '%s'", userName);
                            }
                            evc.setVerified(verified);
                        } else if (credential instanceof ValidatePasswordCredential) {
                            boolean verified = ((ValidatePasswordCredential) credential).validatePassword(guess);
                            if (verified == false) {
                                SECURITY_LOGGER.tracef("Delegated verification failed for user '%s'", userName);
                            }
                            evc.setVerified(verified);
                        }

                    }
                }
            }
        };

    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
               return new RealmIdentityImpl(principal);
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends org.wildfly.security.credential.Credential> credentialType, String algorithmName) throws RealmUnavailableException {
            Assert.checkNotNullParam("credentialType", credentialType);
            return SupportLevel.POSSIBLY_SUPPORTED;
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends org.wildfly.security.credential.Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
            Assert.checkNotNullParam("credentialType", credentialType);
            return SupportLevel.POSSIBLY_SUPPORTED;
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return PasswordGuessEvidence.class.isAssignableFrom(evidenceType) ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
        }

        private class RealmIdentityImpl implements RealmIdentity {

            private final String name;
            private final AuthenticationPlugIn<Credential> ap;;
            private final Principal principal;
            private final Credential credential;

            private RealmIdentityImpl(final Principal principal) throws RealmUnavailableException {
                name = getPlugInName();
                ap = getPlugInLoader().loadAuthenticationPlugIn(name);
                if (ap instanceof PlugInConfigurationSupport) {
                    PlugInConfigurationSupport pcf = (PlugInConfigurationSupport) ap;
                    try {
                        pcf.init(getConfiguration(), SecurityRealmService.SharedStateSecurityRealm.getSharedState());
                    } catch (IOException e) {
                        throw DomainManagementLogger.ROOT_LOGGER.unableToInitialisePlugIn(name, e.getMessage());
                    }
                }

                this.principal = principal;

                try {
                    Identity identity = ap.loadIdentity(principal.getName(), getRealmName());
                    credential = identity != null ? identity.getCredential() : null;
                } catch (IOException e) {
                    throw new RealmUnavailableException(e);
                }
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends org.wildfly.security.credential.Credential> credentialType, String algorithmName) throws RealmUnavailableException {
                return getCredential(credentialType, algorithmName) != null ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends org.wildfly.security.credential.Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                return getCredential(credentialType, algorithmName, parameterSpec) != null ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
            }

            @Override
            public <C extends org.wildfly.security.credential.Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return getCredential(credentialType, null);
            }

            @Override
            public <C extends org.wildfly.security.credential.Credential> C getCredential(Class<C> credentialType, final String algorithmName) throws RealmUnavailableException {
                return getCredential(credentialType, algorithmName, null);
            }

            public <C extends org.wildfly.security.credential.Credential> C getCredential(Class<C> credentialType, final String algorithmName, final AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                if (credential == null || (org.wildfly.security.credential.PasswordCredential.class.isAssignableFrom(credentialType) == false)) {
                    return null;
                }

                final PasswordFactory passwordFactory;
                final PasswordSpec passwordSpec;
                final String realmName = getRealmName();
                final String userName = principal.getName();
                if (credential instanceof DigestCredential && (algorithmName == null || ALGORITHM_DIGEST_MD5.equals(algorithmName))) {
                    if (parameterSpec != null) {
                        if (! (parameterSpec instanceof DigestPasswordAlgorithmSpec)) {
                            // unknown parameters
                            return null;
                        }
                        final DigestPasswordAlgorithmSpec digestSpec = (DigestPasswordAlgorithmSpec) parameterSpec;
                        if (! Objects.equals(digestSpec.getRealm(), realmName) || ! Objects.equals(digestSpec.getUsername(), userName)) {
                            return null;
                        }
                    }
                    passwordFactory = getPasswordFactory(ALGORITHM_DIGEST_MD5);
                    byte[] hashed = ByteIterator.ofBytes(((DigestCredential) credential).getHash().getBytes(StandardCharsets.UTF_8)).asUtf8String().hexDecode().drain();
                    passwordSpec = new DigestPasswordSpec(userName, realmName, hashed);
                } else if (credential instanceof PasswordCredential) {
                    if (algorithmName == null || ALGORITHM_CLEAR.equals(algorithmName)) {
                        passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                        passwordSpec = new ClearPasswordSpec(((PasswordCredential) credential).getPassword());
                    } else if (ALGORITHM_DIGEST_MD5.equals(algorithmName)) {
                        passwordFactory = getPasswordFactory(ALGORITHM_DIGEST_MD5);
                        UsernamePasswordHashUtil hashUtil = getHashUtil();
                        synchronized (hashUtil) {
                            byte[] hashed = hashUtil
                                    .generateHashedHexURP(userName, realmName,
                                            ((PasswordCredential) credential).getPassword()).getBytes(StandardCharsets.UTF_8);
                            passwordSpec = new DigestPasswordSpec(userName, realmName, hashed);
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }

                try {
                    return credentialType.cast(new org.wildfly.security.credential.PasswordCredential(passwordFactory.generatePassword(passwordSpec)));
                } catch (InvalidKeySpecException e) {
                    throw new RealmUnavailableException(e);
                }
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getEvidenceVerifySupport(evidenceType, algorithmName);
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {
                if (credential == null || evidence instanceof PasswordGuessEvidence == false) {
                    return false;
                }
                final char[] guess = ((PasswordGuessEvidence) evidence).getGuess();

                if (credential instanceof PasswordCredential) {
                    boolean verified = Arrays.equals(((PasswordCredential) credential).getPassword(), guess);
                    if (verified == false) {
                        SECURITY_LOGGER.tracef("Password verification failed for user '%s'", principal.getName());
                    }
                    return verified;
                } else if (credential instanceof DigestCredential) {
                    UsernamePasswordHashUtil hashUtil = getHashUtil();
                    String hash;
                    synchronized (hashUtil) {
                        hash = hashUtil.generateHashedHexURP(principal.getName(), getRealmName(), guess);
                    }
                    String expected = ((DigestCredential) credential).getHash();
                    boolean verified = expected.equals(hash);
                    if (verified == false) {
                        SECURITY_LOGGER.tracef("Digest verification failed for user '%s'", principal.getName());
                    }
                    return verified;
                } else if (credential instanceof ValidatePasswordCredential) {
                    boolean verified = ((ValidatePasswordCredential) credential).validatePassword(guess);
                    if (verified == false) {
                        SECURITY_LOGGER.tracef("Delegated verification failed for user '%s'", principal.getName());
                    }
                    return verified;
                }

                return false;
            }

            @Override
            public boolean exists() throws RealmUnavailableException {
                return credential != null;
            }

        }
    }

    private static PasswordFactory getPasswordFactory(final String algorithm) {
        try {
            return PasswordFactory.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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
