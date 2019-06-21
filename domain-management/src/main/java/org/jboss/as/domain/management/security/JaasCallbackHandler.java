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

import static org.jboss.as.domain.management.RealmConfigurationConstants.SUBJECT_CALLBACK_SUPPORTED;
import static org.jboss.as.domain.management.RealmConfigurationConstants.VERIFY_PASSWORD_CALLBACK_SUPPORTED;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;

import java.io.IOException;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

import org.jboss.as.core.security.RealmGroup;
import org.jboss.as.core.security.ServerSecurityManager;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.security.SimpleGroup;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.callback.EvidenceVerifyCallback;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.authz.Attributes;
import org.wildfly.security.authz.AuthorizationIdentity;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.evidence.PasswordGuessEvidence;

/**
 * A CallbackHandler verifying users usernames and passwords by using a JAAS LoginContext.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com>Richard Opalka</a>
 */
public class JaasCallbackHandler implements Service, CallbackHandlerService, CallbackHandler {

    private static final String SERVICE_SUFFIX = "jaas";

    private static final Map<String, String> configurationOptions;

    static {
        Map<String, String> temp = new HashMap<String, String>(2);
        temp.put(SUBJECT_CALLBACK_SUPPORTED, Boolean.TRUE.toString());
        temp.put(VERIFY_PASSWORD_CALLBACK_SUPPORTED, Boolean.TRUE.toString());
        configurationOptions = Collections.unmodifiableMap(temp);
    }

    private final String realm;
    private final String name;
    private final boolean assignGroups;
    private final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer;
    private final Supplier<ServerSecurityManager> securityManagerSupplier;

    JaasCallbackHandler(final Consumer<CallbackHandlerService> callbackHandlerServiceConsumer,
                        final Supplier<ServerSecurityManager> securityManagerSupplier,
                        final String realm, final String name, final boolean assignGroups) {
        this.callbackHandlerServiceConsumer = callbackHandlerServiceConsumer;
        this.securityManagerSupplier = securityManagerSupplier;
        this.realm = realm;
        this.name = name;
        this.assignGroups = assignGroups;
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
        return configurationOptions;
    }

    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        return this;
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        return new SecurityRealmImpl();
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        // Can't check so assume it is ready.
        return true;
    }

    /*
     * CallbackHandler Method
     */

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        if (callbacks.length == 1 && callbacks[0] instanceof AuthorizeCallback) {
            AuthorizeCallback acb = (AuthorizeCallback) callbacks[0];
            boolean authorized = acb.getAuthenticationID().equals(acb.getAuthorizationID());
            if (authorized == false) {
                SECURITY_LOGGER.tracef(
                        "Checking 'AuthorizeCallback', authorized=false, authenticationID=%s, authorizationID=%s.",
                        acb.getAuthenticationID(), acb.getAuthorizationID());
            }
            acb.setAuthorized(authorized);

            return;
        }

        NameCallback nameCallBack = null;
        EvidenceVerifyCallback evidenceVerifyCallback = null;
        SubjectCallback subjectCallback = null;

        for (Callback current : callbacks) {
            if (current instanceof NameCallback) {
                nameCallBack = (NameCallback) current;
            } else if (current instanceof RealmCallback) {
            } else if (current instanceof EvidenceVerifyCallback) {
                evidenceVerifyCallback = (EvidenceVerifyCallback) current;
            } else if (current instanceof SubjectCallback) {
                subjectCallback = (SubjectCallback) current;
            } else {
                throw new UnsupportedCallbackException(current);
            }
        }

        if (nameCallBack == null) {
            SECURITY_LOGGER.trace("No username supplied in Callbacks.");
            throw DomainManagementLogger.ROOT_LOGGER.noUsername();
        }
        final String userName = nameCallBack.getDefaultName();
        if (userName == null || userName.length() == 0) {
            SECURITY_LOGGER.trace("NameCallback either has no username or is 0 length.");
            throw DomainManagementLogger.ROOT_LOGGER.noUsername();
        }
        if (evidenceVerifyCallback == null || evidenceVerifyCallback.getEvidence() == null) {
            SECURITY_LOGGER.trace("No password to verify.");
            throw DomainManagementLogger.ROOT_LOGGER.noPassword();
        }

        final char[] password;

        if (evidenceVerifyCallback.getEvidence() instanceof PasswordGuessEvidence) {
            password = ((PasswordGuessEvidence) evidenceVerifyCallback.getEvidence()).getGuess();
        } else {
            SECURITY_LOGGER.trace("No password to verify.");
            throw DomainManagementLogger.ROOT_LOGGER.noPassword();
        }

        Subject subject = subjectCallback != null && subjectCallback.getSubject() != null ? subjectCallback.getSubject()
                : new Subject();
        evidenceVerifyCallback.setVerified(verify(userName, password, subject, subjectCallback != null ? subjectCallback::setSubject : null));
    }

    private boolean verify(String userName, char[] password, Subject subject, Consumer<Subject> subjectConsumer) {
        ServerSecurityManager securityManager;
        if ((securityManager = securityManagerSupplier != null ? securityManagerSupplier.get() : null) != null) {
            try {
                securityManager.push(name, userName, password, subject);
                securityManager.authenticate();
                subject = securityManager.getSubject();
                subject.getPrivateCredentials().add(new org.jboss.as.domain.management.security.PasswordCredential(userName, password));
                if (assignGroups) {
                    Set<Principal> prinicpals = subject.getPrincipals();
                    Set<SimpleGroup> groups = subject.getPrincipals(SimpleGroup.class);
                    for (SimpleGroup current : groups) {
                        if ("Roles".equals(current.getName())) {
                            Enumeration<Principal> members = current.members();
                            while (members.hasMoreElements()) {
                                prinicpals.add(new RealmGroup(realm, members.nextElement().getName()));
                            }
                        }
                    }
                }
                if (subjectConsumer != null) {
                    // Only want to deliberately pass it back if authentication completed.
                    subjectConsumer.accept(subject);
                }
                return true;
            } catch (SecurityException e) {
                SECURITY_LOGGER.debug("Failed to verify password in JAAS callbackhandler " + this.name, e);
                return false;
            } finally {
                securityManager.pop();
            }

        } else {
            try {
                LoginContext ctx = new LoginContext(name, subject, new CallbackHandler() {

                    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                        for (Callback current : callbacks) {
                            if (current instanceof NameCallback) {
                                NameCallback ncb = (NameCallback) current;
                                ncb.setName(userName);
                            } else if (current instanceof PasswordCallback) {
                                PasswordCallback pcb = (PasswordCallback) current;
                                pcb.setPassword(password);
                            } else {
                                throw new UnsupportedCallbackException(current);
                            }
                        }
                    }
                });
                ctx.login();
                subject.getPrivateCredentials().add(new org.jboss.as.domain.management.security.PasswordCredential(userName, password));
                if (assignGroups) {
                    Set<Principal> prinicpals = subject.getPrincipals();
                    Set<SimpleGroup> groups = subject.getPrincipals(SimpleGroup.class);
                    for (SimpleGroup current : groups) {
                        if ("Roles".equals(current.getName())) {
                            Enumeration<Principal> members = current.members();
                            while (members.hasMoreElements()) {
                                prinicpals.add(new RealmGroup(realm, members.nextElement().getName()));
                            }
                        }
                    }
                }
                if (subjectConsumer != null) {
                    // Only want to deliberately pass it back if authentication completed.
                    subjectConsumer.accept(subject);
                }
                return true;
            } catch (LoginException e) {
                SECURITY_LOGGER.debug("Login failed in JAAS callbackhandler " + this.name, e);
                return false;
            }
        }
    }

    private class SecurityRealmImpl implements org.wildfly.security.auth.server.SecurityRealm {

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            return new RealmIdentityImpl(principal);
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
            Assert.checkNotNullParam("credentialType", credentialType);
            return SupportLevel.UNSUPPORTED;
        }

        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
            Assert.checkNotNullParam("credentialType", credentialType);
            return SupportLevel.UNSUPPORTED;
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return PasswordGuessEvidence.class.isAssignableFrom(evidenceType) ? SupportLevel.SUPPORTED : SupportLevel.UNSUPPORTED;
        }

        private class RealmIdentityImpl implements RealmIdentity {

            private final Principal principal;
            private volatile Subject subject = new Subject();

            private RealmIdentityImpl(final Principal principal) {
                this.principal = principal;
            }

            @Override
            public Principal getRealmIdentityPrincipal() {
                return principal;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName, parameterSpec);
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getCredentialAcquireSupport(credentialType, algorithmName);
            }

            @Override
            public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                return null;
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
                return SecurityRealmImpl.this.getEvidenceVerifySupport(evidenceType, algorithmName);
            }

            @Override
            public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {
                if (evidence instanceof PasswordGuessEvidence == false) {
                    return false;
                }
                final char[] guess = ((PasswordGuessEvidence) evidence).getGuess();

                return verify(principal.getName(), guess, subject, s -> subject = s);
            }

            @Override
            public boolean exists()  {
                return true;
            }

            @Override
            public AuthorizationIdentity getAuthorizationIdentity() throws RealmUnavailableException {
                List<String> list = new ArrayList<>();
                for (RealmGroup realmGroup : subject.getPrincipals(RealmGroup.class)) {
                    String realmGroupName = realmGroup.getName();
                    list.add(realmGroupName);
                }
                Attributes attributes = new MapAttributes(Collections.singletonMap("GROUPS",
                    list));
                return AuthorizationIdentity.basicIdentity(attributes);
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
