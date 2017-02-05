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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PASSWORD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.domain.management.RealmConfigurationConstants.DIGEST_PLAIN_TEXT;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;
import static org.wildfly.security.password.interfaces.ClearPassword.ALGORITHM_CLEAR;
import static org.wildfly.security.password.interfaces.DigestPassword.ALGORITHM_DIGEST_MD5;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;

/**
 * A CallbackHandler for users defined within the domain model.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class UserDomainCallbackHandler implements Service<CallbackHandlerService>, CallbackHandlerService, CallbackHandler {

    private static final String SERVICE_SUFFIX = "users";

    private final String realm;

    private volatile ModelNode userDomain;

    public UserDomainCallbackHandler(String realm, ModelNode userDomain) {
        this.realm = realm;
        setUserDomain(userDomain);
    }

    void setUserDomain(final ModelNode userDomain) {
        this.userDomain = userDomain == null || !userDomain.isDefined() ? new ModelNode().setEmptyObject() : userDomain.clone();
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
        return Collections.singletonMap(DIGEST_PLAIN_TEXT, Boolean.TRUE.toString());
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        return true;
    }

    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        return this;
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    /*
     *  Service Methods
     */

    public void start(StartContext context) throws StartException {
    }

    public void stop(StopContext context) {
    }

    public UserDomainCallbackHandler getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    /*
     *  CallbackHandler Method
     */

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

        final ModelNode userMap = this.userDomain;

        List<Callback> toRespondTo = new LinkedList<Callback>();

        String userName = null;
        ModelNode user = null;

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
                if (userMap.get(USER).hasDefined(userName)) {
                    user = userMap.get(USER, userName);
                }
            } else if (current instanceof PasswordCallback) {
                toRespondTo.add(current);
            } else if (current instanceof RealmCallback) {
                String realm = ((RealmCallback) current).getDefaultText();
                if (this.realm.equals(realm) == false) {
                    // TODO - Check if this needs a real error or of just an unexpected internal error.
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
                if (user == null) {
                    SECURITY_LOGGER.tracef("User '%s' not found.", userName);
                    throw new UserNotFoundException(userName);
                }
                String password = user.require(PASSWORD).asString();
                ((PasswordCallback) current).setPassword(password.toCharArray());
            }
        }

    }

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            ModelNode userMap = userDomain;
            String name = principal.getName();
            final ModelNode user = userMap.get(USER).hasDefined(name) ? userMap.get(USER, name) : null;

            return new RealmIdentityImpl(principal, user);
        }

        @Override
        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
            Assert.checkNotNullParam("credentialType", credentialType);
            return PasswordCredential.class.isAssignableFrom(credentialType) && (algorithmName == null || algorithmName.equals(ALGORITHM_CLEAR) ||
                    algorithmName.equals(ALGORITHM_DIGEST_MD5)) ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return PasswordGuessEvidence.class.isAssignableFrom(evidenceType) ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
        }

        private class RealmIdentityImpl implements RealmIdentity {

            private final Principal principal;
            private final ModelNode user;

            private RealmIdentityImpl(final Principal principal, final ModelNode user) {
                this.principal = principal;
                this.user = user;
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            @Override
            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return getCredential(credentialType, null);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType, final String algorithmName) throws RealmUnavailableException {
                if (user == null || (PasswordCredential.class.isAssignableFrom(credentialType) == false)) {
                    return null;
                }

                boolean clear;
                if (algorithmName == null || ALGORITHM_CLEAR.equals(algorithmName)) {
                    clear = true;
                } else if (ALGORITHM_DIGEST_MD5.equals(algorithmName)) {
                    clear = false;
                } else {
                    return null;
                }

                final PasswordFactory passwordFactory;
                final PasswordSpec passwordSpec;

                String password = user.require(PASSWORD).asString();
                if (clear) {
                    passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                    passwordSpec = new ClearPasswordSpec(password.toCharArray());
                } else {
                    passwordFactory = getPasswordFactory(ALGORITHM_DIGEST_MD5);
                    AlgorithmParameterSpec algorithmParameterSpec = new DigestPasswordAlgorithmSpec(principal.getName(), realm);
                    passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), algorithmParameterSpec);
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
                if (user == null || evidence instanceof PasswordGuessEvidence == false) {
                    return false;
                }
                final char[] guess = ((PasswordGuessEvidence) evidence).getGuess();

                String password = user.require(PASSWORD).asString();
                final PasswordFactory passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                final PasswordSpec passwordSpec = new ClearPasswordSpec(password.toCharArray());
                final Password actualPassword;

                try {
                    actualPassword = passwordFactory.generatePassword(passwordSpec);

                    return passwordFactory.verify(actualPassword, guess);
                } catch (InvalidKeySpecException | InvalidKeyException | IllegalStateException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public boolean exists() throws RealmUnavailableException {
                return user != null;
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
