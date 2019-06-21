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

import static org.jboss.as.domain.management.RealmConfigurationConstants.DIGEST_PLAIN_TEXT;
import static org.jboss.as.domain.management.RealmConfigurationConstants.VERIFY_PASSWORD_CALLBACK_SUPPORTED;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;
import static org.wildfly.security.password.interfaces.ClearPassword.ALGORITHM_CLEAR;
import static org.wildfly.security.password.interfaces.DigestPassword.ALGORITHM_DIGEST_MD5;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
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

import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.Assert;
import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.callback.EvidenceVerifyCallback;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.DigestPasswordSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;

/**
 * A CallbackHandler obtaining the users and their passwords from a properties file.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class PropertiesCallbackHandler extends UserPropertiesFileLoader implements Service,
        CallbackHandlerService, CallbackHandler {

    private static final String SERVICE_SUFFIX = "properties_authentication";

    private static UsernamePasswordHashUtil hashUtil = null;

    private final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer;
    private final String realm;
    private final boolean plainText;

    PropertiesCallbackHandler(final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer,
                              final Supplier<PathManager> pathManagerSupplier,
                              final String realm, final String path, final String relativeTo, final boolean plainText) {
        super(pathManagerSupplier, path, relativeTo);
        this.callbackHandlerServiceConsumer = callbackHandlerServiceConsumer;
        this.realm = realm;
        this.plainText = plainText;
    }

    /*
     * CallbackHandlerService Methods
     */

    public AuthMechanism getPreferredMechanism() {
        return AuthMechanism.DIGEST;
    }

    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.singleton(AuthMechanism.PLAIN);
    }

    public Map<String, String> getConfigurationOptions() {
        Map<String, String> response = new HashMap<String, String>(2);
        response.put(DIGEST_PLAIN_TEXT, Boolean.toString(plainText));
        response.put(VERIFY_PASSWORD_CALLBACK_SUPPORTED, Boolean.TRUE.toString());
        return response;
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        Properties users;
        try {
            users = getProperties();
        } catch (IOException e) {
            return false;
        }
        return (users.size() > 0);
    }

    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        return this;
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    /*
     * Service Methods
     */



    @Override
    protected void verifyProperties(Properties properties) throws IOException {
        final String admin = "admin";
        if (properties.contains(admin) && admin.equals(properties.get(admin))) {
            ROOT_LOGGER.userAndPasswordWarning();
        }
    }

    @Override
    public void start(final StartContext context) throws StartException {
        super.start(context);
        try {
            String fileRealm = getRealmName();
            if (fileRealm != null && realm.equals(getRealmName()) == false) {
                ROOT_LOGGER.realmMisMatch(realm, fileRealm);
            }
        } catch (IOException e) {
            throw DomainManagementLogger.ROOT_LOGGER.unableToLoadProperties(e);
        }
        callbackHandlerServiceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        callbackHandlerServiceConsumer.accept(null);
        super.stop(context);
    }

    /*
     * CallbackHandler Methods
     */

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        List<Callback> toRespondTo = new LinkedList<Callback>();

        String userName = null;
        boolean userFound = false;

        Properties users = getProperties();

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
                userFound = users.containsKey(userName);
            } else if (current instanceof PasswordCallback && plainText) {
                toRespondTo.add(current);
            } else if (current instanceof CredentialCallback) {
                toRespondTo.add(current);
            } else if (current instanceof EvidenceVerifyCallback && ((EvidenceVerifyCallback)current).getEvidence() instanceof PasswordGuessEvidence) {
                toRespondTo.add(current);
            } else if (current instanceof RealmCallback) {
                String realm = ((RealmCallback) current).getDefaultText();
                if (this.realm.equals(realm) == false) {
                    throw DomainManagementLogger.ROOT_LOGGER.invalidRealm(realm, this.realm);
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
                if (userFound == false) {
                    SECURITY_LOGGER.tracef("User '%s' not found in properties file.", userName);
                    throw new UserNotFoundException(userName);
                }
                String password = users.get(userName).toString();
                ((PasswordCallback) current).setPassword(password.toCharArray());
            } else if (current instanceof CredentialCallback) {
                if (userFound == false) {
                    SECURITY_LOGGER.tracef("User '%s' not found in properties file.", userName);
                    throw new UserNotFoundException(userName);
                }

                CredentialCallback cc = (CredentialCallback) current;
                if (PasswordCredential.class.isAssignableFrom(cc.getCredentialType())) {
                    String algorithmName = cc.getAlgorithm();
                    final Password password;
                    if ((algorithmName == null || ALGORITHM_CLEAR.equals(algorithmName)) && plainText) {
                        password = ClearPassword.createRaw(ALGORITHM_CLEAR, ((String) users.get(userName)).toCharArray());
                    } else if ((algorithmName == null || ALGORITHM_DIGEST_MD5.equals(algorithmName)) && plainText == false) {
                        byte[] hashed = ByteIterator.ofBytes(((String) users.get(userName)).getBytes(StandardCharsets.UTF_8)).asUtf8String().hexDecode().drain();
                        password = DigestPassword.createRaw(ALGORITHM_DIGEST_MD5, userName, realm, hashed);
                    } else {
                        continue;
                    }
                    cc.setCredential(cc.getCredentialType().cast(new PasswordCredential(password)));
                }
            } else if (current instanceof EvidenceVerifyCallback) {
                if (userFound == false) {
                    SECURITY_LOGGER.tracef("User '%s' not found in properties file.", userName);
                    throw new UserNotFoundException(userName);
                }
                EvidenceVerifyCallback evc = (EvidenceVerifyCallback) current;
                PasswordGuessEvidence evidence = (PasswordGuessEvidence) evc.getEvidence();
                char[] guess = evidence.getGuess();
                if (plainText) {
                    String password = users.get(userName).toString();
                    boolean verified = password.equals(new String(guess));
                    if (verified == false) {
                        SECURITY_LOGGER.tracef("Password verification failed for user '%s'", userName);
                    }
                    evc.setVerified(verified);
                } else {
                    UsernamePasswordHashUtil hashUtil = getHashUtil();
                    String hash;
                    synchronized (hashUtil) {
                        hash = hashUtil.generateHashedHexURP(userName, realm, guess);
                    }
                    String expected = users.get(userName).toString();
                    boolean verified = expected.equals(hash);
                    if (verified == false) {
                        SECURITY_LOGGER.tracef("Digest verification failed for user '%s'", userName);
                    }
                    evc.setVerified(verified);
                }
            }
        }
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

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            try {
                Properties users = getProperties();

                String name = principal.getName();
                return new RealmIdentityImpl(principal, users.getProperty(name));
            } catch (IOException e) {
                throw new RealmUnavailableException(e);
            }
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
            return getCredentialAcquireSupport(credentialType, algorithmName, null);
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
            Assert.checkNotNullParam("credentialType", credentialType);
            return PasswordCredential.class.isAssignableFrom(credentialType) && (algorithmName == null || algorithmName.equals(ALGORITHM_CLEAR) && plainText ||
                    algorithmName.equals(ALGORITHM_DIGEST_MD5)) && parameterSpec == null ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return PasswordGuessEvidence.class.isAssignableFrom(evidenceType) ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
        }

        private class RealmIdentityImpl implements RealmIdentity {

            private final Principal principal;
            private final String password;

            private RealmIdentityImpl(final Principal principal, final String password) {
                this.principal = principal;
                this.password = password;
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName);
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName, parameterSpec);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return getCredential(credentialType, null);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType, final String algorithmName) throws RealmUnavailableException {
                return getCredential(credentialType, algorithmName, null);
            }

            public <C extends Credential> C getCredential(Class<C> credentialType, final String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                if (password == null || (PasswordCredential.class.isAssignableFrom(credentialType) == false)) {
                    return null;
                }

                boolean clear;
                if (algorithmName == null) {
                    clear = plainText;
                } else if (ALGORITHM_CLEAR.equals(algorithmName)) {
                    clear = true;
                } else if (ALGORITHM_DIGEST_MD5.equals(algorithmName)) {
                    clear = false;
                    if (parameterSpec != null) {
                        if (! (parameterSpec instanceof DigestPasswordAlgorithmSpec)) {
                            return null;
                        }
                        final DigestPasswordAlgorithmSpec digestSpec = (DigestPasswordAlgorithmSpec) parameterSpec;
                        if (! Objects.equals(digestSpec.getRealm(), realm) || ! Objects.equals(digestSpec.getUsername(), principal.getName())) {
                            return null;
                        }
                    }
                } else {
                    return null;
                }

                final PasswordFactory passwordFactory;
                final PasswordSpec passwordSpec;

                if (clear) {
                    passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                    passwordSpec = new ClearPasswordSpec(password.toCharArray());
                } else {
                    passwordFactory = getPasswordFactory(ALGORITHM_DIGEST_MD5);
                    if (plainText) {
                        AlgorithmParameterSpec algorithmParameterSpec = new DigestPasswordAlgorithmSpec(principal.getName(), realm);
                        passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), algorithmParameterSpec);
                    } else {
                        byte[] hashed = ByteIterator.ofBytes(password.getBytes(StandardCharsets.UTF_8)).asUtf8String().hexDecode().drain();
                        passwordSpec = new DigestPasswordSpec(principal.getName(), realm, hashed);
                    }
                }

                try {
                    return credentialType.cast(new PasswordCredential(passwordFactory.generatePassword(passwordSpec)));
                } catch (InvalidKeySpecException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getEvidenceVerifySupport(evidenceType, algorithmName);
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {
                if (password == null || evidence instanceof PasswordGuessEvidence == false) {
                    return false;
                }
                final char[] guess = ((PasswordGuessEvidence) evidence).getGuess();

                final PasswordFactory passwordFactory;
                final PasswordSpec passwordSpec;
                final Password actualPassword;
                if (plainText) {
                    passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                    passwordSpec = new ClearPasswordSpec(password.toCharArray());
                } else {
                    passwordFactory = getPasswordFactory(ALGORITHM_DIGEST_MD5);

                    byte[] hashed = ByteIterator.ofBytes(password.getBytes(StandardCharsets.UTF_8)).asUtf8String().hexDecode().drain();
                    passwordSpec = new DigestPasswordSpec(principal.getName(), realm, hashed);
                }
                try {
                    actualPassword = passwordFactory.generatePassword(passwordSpec);

                    return passwordFactory.verify(actualPassword, guess);
                } catch (InvalidKeySpecException | InvalidKeyException | IllegalStateException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public boolean exists() throws RealmUnavailableException {
                return password != null;
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
