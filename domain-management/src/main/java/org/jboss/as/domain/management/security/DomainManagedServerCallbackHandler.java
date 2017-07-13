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
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedSetValue;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.Assert;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;

import static org.jboss.as.domain.management.RealmConfigurationConstants.DIGEST_PLAIN_TEXT;
import static org.wildfly.security.password.interfaces.ClearPassword.ALGORITHM_CLEAR;
import static org.wildfly.security.password.interfaces.DigestPassword.ALGORITHM_DIGEST_MD5;

/**
 * A callback handler for servers connecting back to the local host-controller
 * When a server is started, the process is given a generated authKey from the initiating host-controller, that it
 * uses to connect back to the host-controller after start {@see HostControllerConnection}.
 *
 * @author <a href="mailto:kwills@jboss.com">Ken Wills</a>
 */
public class DomainManagedServerCallbackHandler implements Service<CallbackHandlerService>, CallbackHandlerService, CallbackHandler {

    private static final String SERVICE_SUFFIX = "servers";
    private volatile CallbackHandler serverCallbackHandler;
    // this realm is only used internally for server to host controller communication
    public static final String REALM_NAME = "internal-domain-servers-security-realm";
    private final InjectedValue<Map<String, ExceptionSupplier<CredentialSource, Exception>>> credentialSourceSupplier = new InjectedValue<>();

    // the callback handler passed through from ServerInventory, containing the server authkeys.
    // we don't have the ManagedServer etc classes here, so this seems to be the cleanest method of doing it.
    public void setServerCallbackHandler(final CallbackHandler serverCallbackHandler) {
        this.serverCallbackHandler = serverCallbackHandler;
    }

    Injector<Map<String, ExceptionSupplier<CredentialSource, Exception>>> getCredentialSourceSupplierInjector() {
        return credentialSourceSupplier;
    }

    /*
     * CallbackHandlerService Methods
     */
    public AuthMechanism getPreferredMechanism() {
        return AuthMechanism.PLAIN;
    }

    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.EMPTY_SET;
    }

    public Map<String, String> getConfigurationOptions() {
        return Collections.singletonMap(DIGEST_PLAIN_TEXT, Boolean.TRUE.toString());
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        return false;
    }

    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        return this;
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new DomainServerSecurityRealmImpl();
    }

    /*
     *  Service Methods
     */

    public void start(StartContext context) throws StartException {
    }

    public void stop(StopContext context) {
    }

    public DomainManagedServerCallbackHandler getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public void installServerSecurityRealm(final OperationContext context, final SecurityRealmService securityRealmService, final ServiceTarget serviceTarget,
                                           final ServiceBuilder<?> realmBuilder, final Injector<CallbackHandlerService> injector) throws OperationFailedException {
        ServiceName domainServersServiceName = DomainManagedServerCallbackHandler.ServiceUtil.createServiceName();
        ServiceRegistry serviceRegistry = context.getServiceRegistry(false);
        // this realm is only created once.
        if (serviceRegistry.getService(domainServersServiceName) != null) {
            return;
        }

        InjectedSetValue<CallbackHandlerService> injectorSet = securityRealmService.getCallbackHandlerService();
        ServiceBuilder<CallbackHandlerService> serviceBuilder = serviceTarget.addService(domainServersServiceName, this)
                .setInitialMode(ServiceController.Mode.ON_DEMAND);
        serviceBuilder.install();
        CallbackHandlerService.ServiceUtil.addDependency(realmBuilder, injectorSet.injector(), domainServersServiceName);
    }

    /*
     *  CallbackHandler Method
     */

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        serverCallbackHandler.handle((callbacks));
    }

    private class DomainServerSecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

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
            return SupportLevel.UNSUPPORTED;
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

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                return DomainManagedServerCallbackHandler.DomainServerSecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName, parameterSpec);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return getCredential(credentialType, null);
            }

            public <C extends Credential> C getCredential(Class<C> credentialType, final String algorithmName) throws RealmUnavailableException {
                return getCredential(credentialType, algorithmName, null);
            }

            public <C extends Credential> C getCredential(Class<C> credentialType, final String algorithmName, final AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {

                if (serverName == null || (PasswordCredential.class.isAssignableFrom(credentialType) == false)) {
                    return null;
                }

                final PasswordFactory passwordFactory;
                final PasswordSpec passwordSpec;

                char[] password;
                try {
                    password = fetchCredential(serverName);
                } catch (Exception e) {
                    throw new RealmUnavailableException(e);
                }

                passwordFactory = getPasswordFactory(ALGORITHM_CLEAR);
                passwordSpec = new ClearPasswordSpec(password);

                try {
                    return credentialType.cast(new PasswordCredential(passwordFactory.generatePassword(passwordSpec)));
                } catch (InvalidKeySpecException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
                return DomainManagedServerCallbackHandler.DomainServerSecurityRealmImpl.this.getEvidenceVerifySupport(evidenceType, algorithmName);
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {

                /*
                // only support the internal server realm REALM_NAME
                RealmUser realmUser;
                if (principal instanceof RealmUser) {
                    realmUser = (RealmUser)principal;
                    if (!REALM_NAME.equals(realmUser.getRealm())) {
                        throw DomainManagementLogger.ROOT_LOGGER.invalidRealm(realmUser.getRealm(), REALM_NAME);
                    }
                } else {
                    throw new RealmUnavailableException(DomainManagementLogger.ROOT_LOGGER.realmMustBeSpecified());
                }
                */

                if (serverName == null || evidence instanceof PasswordGuessEvidence == false) {
                    return false;
                }

                char[] password;
                final char[] guess = ((PasswordGuessEvidence) evidence).getGuess();
                try {
                    password = fetchCredential(serverName);
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

        public static ServiceName createServiceName() {
            return SecurityRealm.ServiceUtil.createServiceName(REALM_NAME).append(SERVICE_SUFFIX);
        }

    }

}
