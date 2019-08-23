/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.domain.management.security;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jboss.as.core.security.RealmUser;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
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
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;

import static org.jboss.as.domain.management.RealmConfigurationConstants.VERIFY_PASSWORD_CALLBACK_SUPPORTED;
import static org.jboss.msc.service.ServiceController.Mode.ON_DEMAND;
import static org.wildfly.security.password.interfaces.ClearPassword.ALGORITHM_CLEAR;
import static org.wildfly.security.password.interfaces.DigestPassword.ALGORITHM_DIGEST_MD5;

/**
 * A callback handler for servers connecting back to the local host-controller
 * When a server is started, the process is given a generated authKey from the initiating host-controller, that it
 * uses to connect back to the host-controller after start {@see HostControllerConnection}.
 *
 * @author <a href="mailto:kwills@jboss.com">Ken Wills</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DomainManagedServerCallbackHandler implements Service, CallbackHandlerService, CallbackHandler {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("domain", "management", "security", "server-auth");

    public static final String DOMAIN_SERVER_AUTH_REALM = System.getProperty("org.jboss.as.domain.management.security.domain-auth-realm-name", "internal-domain-server-auth-realm");
    public static final String DOMAIN_SERVER_AUTH_PREFIX = System.getProperty("org.jboss.as.domain.management.security.domain-auth-server-prefix", "=");

    private static final String SERVICE_SUFFIX = "internal-domain-server-authentication";
    private final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer;
    private volatile CallbackHandler serverCallbackHandler;

    DomainManagedServerCallbackHandler(final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer) {
        this.callbackHandlerServiceConsumer = callbackHandlerServiceConsumer;
    }

    public static void install(final ServiceTarget serviceTarget) {
        final ServiceBuilder<?> builder = serviceTarget.addService(DomainManagedServerCallbackHandler.SERVICE_NAME);
        final Consumer<CallbackHandlerService> chsConsumer = builder.provides(DomainManagedServerCallbackHandler.SERVICE_NAME);
        builder.setInstance(new DomainManagedServerCallbackHandler(chsConsumer));
        builder.setInitialMode(ON_DEMAND);
        builder.install();
    }

    // the callback handler passed through from ServerInventory, containing the server authkeys.
    // we don't have the ManagedServer etc classes here, so this seems to be the cleanest method of doing it.
    public void setServerCallbackHandler(final CallbackHandler serverCallbackHandler) {
        if (this.serverCallbackHandler != null) {
            throw new UnsupportedOperationException();
        }
        this.serverCallbackHandler = serverCallbackHandler;
    }

    /*
     * CallbackHandlerService Methods
     */
    @Override
    public AuthMechanism getPreferredMechanism() {
       return AuthMechanism.PLAIN;
    }

    @Override
    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, String> getConfigurationOptions() {
        return Collections.singletonMap(VERIFY_PASSWORD_CALLBACK_SUPPORTED, Boolean.TRUE.toString());
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        return true;
    }

    @Override
    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        return this;
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    @Override
    public void start(final StartContext context) throws StartException {
        callbackHandlerServiceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        callbackHandlerServiceConsumer.accept(null);
    }

    /*
     *  CallbackHandler Method
     */
    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        final CallbackHandler serverCallbackHandler = this.serverCallbackHandler;
        if (serverCallbackHandler != null) {
            serverCallbackHandler.handle((callbacks));
        }
    }

    @Override
    public Function<Principal, Principal> getPrincipalMapper() {
        return p -> {
            return p instanceof RealmUser ? new RealmUser(DOMAIN_SERVER_AUTH_REALM, p.getName()) : p;
        };
    }

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            String name = principal.getName();
            return new RealmIdentityImpl(principal, name);
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
            Assert.checkNotNullParam("credentialType", credentialType);
            return PasswordCredential.class.isAssignableFrom(credentialType) && (algorithmName == null || algorithmName.equals(ALGORITHM_CLEAR) ||
                    algorithmName.equals(ALGORITHM_DIGEST_MD5)) ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return SupportLevel.SUPPORTED;
        }

        private class RealmIdentityImpl implements RealmIdentity {

            private final Principal principal;
            private final String serverName;

            private RealmIdentityImpl(final Principal principal, final String serverName) {
                this.principal = principal;
                this.serverName = serverName;
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            @Override
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

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType, final String algorithmName, final AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {

                if (serverName == null || (PasswordCredential.class.isAssignableFrom(credentialType) == false)) {
                    return null;
                }

                final PasswordFactory passwordFactory;
                final PasswordSpec passwordSpec;

                char[] password;
                try {
                    password = fetchCredential(serverName);
                    if (password == null) {
                        return null;
                    }
                } catch (Exception e) {
                    throw new RealmUnavailableException(e);
                }

                if ((algorithmName == null || ALGORITHM_CLEAR.equals(algorithmName))) {
                    passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                    passwordSpec = new ClearPasswordSpec(password);
                    try {
                        return credentialType.cast(new PasswordCredential(passwordFactory.generatePassword(passwordSpec)));
                    } catch (InvalidKeySpecException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (ALGORITHM_DIGEST_MD5.equals(algorithmName)) {
                    try {
                        final PasswordFactory instance = PasswordFactory.getInstance(DigestPassword.ALGORITHM_DIGEST_MD5);
                        final Password pwd = instance.generatePassword(new EncryptablePasswordSpec(password, new DigestPasswordAlgorithmSpec(serverName, DOMAIN_SERVER_AUTH_REALM)));
                        return credentialType.cast(new PasswordCredential(pwd));
                    } catch (Exception e) {
                        throw new RealmUnavailableException(e);
                    }
                } else {
                    throw DomainManagementLogger.ROOT_LOGGER.unableToObtainCredential(serverName);
                }
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getEvidenceVerifySupport(evidenceType, algorithmName);
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {

                if (serverName == null || evidence instanceof PasswordGuessEvidence == false) {
                    return false;
                }

                char[] password;
                final char[] guess = ((PasswordGuessEvidence) evidence).getGuess();
                try {
                    password = fetchCredential(serverName);
                    if (password == null) {
                        return false;
                    }
                } catch (Exception e) {
                    throw new RealmUnavailableException(e);
                }

                final PasswordFactory passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                final PasswordSpec passwordSpec = new ClearPasswordSpec(password);
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
                return serverName != null;
            }
        }
    }

    private char[] fetchCredential(final String serverName) throws UnsupportedCallbackException, IOException {
        final CallbackHandler serverCallbackHandler = this.serverCallbackHandler;
        if (serverCallbackHandler == null) {
            throw DomainManagementLogger.ROOT_LOGGER.callbackHandlerNotInitialized(serverName);
        }
        final List<Callback> callbacks = new ArrayList<>();
        final NameCallback nc = new NameCallback("None", serverName);
        callbacks.add(nc);
        final PasswordCallback pc = new PasswordCallback("Password: ", false);
        callbacks.add(pc);
        serverCallbackHandler.handle(callbacks.toArray(new Callback[callbacks.size()]));
        return pc.getPassword();
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
