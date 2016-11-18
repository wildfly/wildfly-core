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

import static org.jboss.as.domain.management.RealmConfigurationConstants.LOCAL_DEFAULT_USER;
import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;
import static org.jboss.as.domain.management.RealmConfigurationConstants.SUBJECT_CALLBACK_SUPPORTED;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.security.Provider;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.net.ssl.SSLContext;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.SaslServerFactory;

import org.jboss.as.core.security.RealmGroup;
import org.jboss.as.core.security.RealmRole;
import org.jboss.as.core.security.RealmSubjectUserInfo;
import org.jboss.as.core.security.RealmUser;
import org.jboss.as.core.security.SubjectUserInfo;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.AuthorizingCallbackHandler;
import org.jboss.as.domain.management.CallbackHandlerFactory;
import org.jboss.as.domain.management.SubjectIdentity;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedSetValue;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.realm.AggregateSecurityRealm;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;
import org.wildfly.security.auth.server.IdentityLocator;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.MechanismRealmConfiguration;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SupportLevel;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.http.HttpServerAuthenticationMechanismFactory;
import org.wildfly.security.http.util.FilterServerMechanismFactory;
import org.wildfly.security.http.util.SecurityProviderServerMechanismFactory;
import org.wildfly.security.http.util.SetMechanismInformationMechanismFactory;
import org.wildfly.security.sasl.localuser.LocalUserServer;
import org.wildfly.security.sasl.util.FilterMechanismSaslServerFactory;
import org.wildfly.security.sasl.util.PropertiesSaslServerFactory;
import org.wildfly.security.sasl.util.SecurityProviderSaslServerFactory;
import org.wildfly.security.sasl.util.SortedMechanismSaslServerFactory;

/**
 * The service representing the security realm, this service will be injected into any management interfaces
 * requiring any of the capabilities provided by the realm.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SecurityRealmService implements Service<SecurityRealm>, SecurityRealm {

    public static final String LOADED_USERNAME_KEY = SecurityRealmService.class.getName() + ".LOADED_USERNAME";
    public static final String SKIP_GROUP_LOADING_KEY = SecurityRealmService.class.getName() + ".SKIP_GROUP_LOADING";

    private final InjectedValue<SubjectSupplementalService> subjectSupplemental = new InjectedValue<SubjectSupplementalService>();
    private final InjectedValue<SSLContext> sslContext = new InjectedValue<SSLContext>();

    private final InjectedValue<CallbackHandlerFactory> secretCallbackFactory = new InjectedValue<CallbackHandlerFactory>();
    private final InjectedValue<KeytabIdentityFactoryService> keytabFactory = new InjectedValue<KeytabIdentityFactoryService>();
    private final InjectedSetValue<CallbackHandlerService> callbackHandlerServices = new InjectedSetValue<CallbackHandlerService>();

    private final InjectedValue<String> tmpDirPath = new InjectedValue<>();

    private final String name;
    private final boolean mapGroupsToRoles;
    private final Map<AuthMechanism, CallbackHandlerService> registeredServices = new HashMap<AuthMechanism, CallbackHandlerService>();
    private SaslAuthenticationFactory saslAuthenticationFactory = null;
    private HttpAuthenticationFactory httpAuthenticationFactory = null;

    public SecurityRealmService(String name, boolean mapGroupsToRoles) {
        this.name = name;
        this.mapGroupsToRoles = mapGroupsToRoles;
    }

    /*
     * Service Methods
     */

    public void start(StartContext context) throws StartException {
        ROOT_LOGGER.debugf("Starting '%s' Security Realm Service", name);
        for (CallbackHandlerService current : callbackHandlerServices.getValue()) {
            AuthMechanism mechanism = current.getPreferredMechanism();
            if (registeredServices.containsKey(mechanism)) {
                registeredServices.clear();
                throw DomainManagementLogger.ROOT_LOGGER.multipleCallbackHandlerForMechanism(mechanism.name());
            }
            registeredServices.put(mechanism, current);
        }

        /*
         * Create the Elytron authentication factories.
         */

        final Map<String, String> mechanismConfiguration = new HashMap<>();
        final Map<AuthMechanism, MechanismConfiguration> configurationMap = new HashMap<>();

        SubjectSupplementalService subjectSupplementalService = this.subjectSupplemental.getOptionalValue();
        org.wildfly.security.auth.server.SecurityRealm authorizationRealm = subjectSupplementalService != null ? subjectSupplementalService.getElytronSecurityRealm() : org.wildfly.security.auth.server.SecurityRealm.EMPTY_REALM;

        SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        for (Entry<AuthMechanism, CallbackHandlerService> currentRegistration : registeredServices.entrySet()) {
            CallbackHandlerService currentService = currentRegistration.getValue();
            org.wildfly.security.auth.server.SecurityRealm elytronRealm = currentService.getElytronSecurityRealm();
            if (elytronRealm != null) {
                final AuthMechanism mechanism = currentRegistration.getKey();

                domainBuilder.addRealm(mechanism.toString(),
                        currentService.allowGroupLoading() ? new SharedStateSecurityRealm(new AggregateSecurityRealm(elytronRealm, authorizationRealm)) : elytronRealm)
                        .setRoleDecoder(RoleDecoder.simple("GROUPS"))
                        .build();
                configurationMap.put(mechanism,
                        MechanismConfiguration.builder()
                            .setRealmMapper((n, p, e) -> mechanism.toString())
                            .addMechanismRealm(MechanismRealmConfiguration.builder().setRealmName(name).build())
                            .build());
                for (Entry<String, String> currentOption : currentRegistration.getValue().getConfigurationOptions().entrySet()) {
                    switch (currentOption.getKey()) {
                        case LOCAL_DEFAULT_USER:
                            mechanismConfiguration.put(LocalUserServer.DEFAULT_USER, currentOption.getValue());
                            break;
                    }
                }
            }
        }
        mechanismConfiguration.put(LocalUserServer.LEGACY_LOCAL_USER_CHALLENGE_PATH, getAuthDir(tmpDirPath.getValue()));

        domainBuilder.addRealm("EMPTY", org.wildfly.security.auth.server.SecurityRealm.EMPTY_REALM).build();
        domainBuilder.setDefaultRealmName("EMPTY");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> LoginPermission.getInstance());

        SecurityDomain securityDomain = domainBuilder.build();

        HttpAuthenticationFactory.Builder httpBuilder = HttpAuthenticationFactory.builder();
        httpBuilder.setSecurityDomain(securityDomain);

        HttpServerAuthenticationMechanismFactory httpServerFactory = new SecurityProviderServerMechanismFactory(() -> new Provider[] {new WildFlyElytronProvider()});
        httpServerFactory = new SetMechanismInformationMechanismFactory(httpServerFactory);
        httpServerFactory = new FilterServerMechanismFactory(httpServerFactory, (s) -> {
            AuthMechanism mechanism = toAuthMechanism("HTTP", s);
            return mechanism != null && configurationMap.containsKey(mechanism);
        });

        httpBuilder.setFactory(httpServerFactory);
        httpBuilder.setMechanismConfigurationSelector((mi) -> {
            AuthMechanism mechanism = toAuthMechanism(mi.getMechanismType(), mi.getMechanismName());
            if (mechanism != null) {
                return configurationMap.get(mechanism);
            }
            return null;
        });
        httpAuthenticationFactory = httpBuilder.build();

        SaslAuthenticationFactory.Builder saslBuilder = SaslAuthenticationFactory.builder();
        saslBuilder.setSecurityDomain(securityDomain);

        SaslServerFactory saslServerFactory = new SecurityProviderSaslServerFactory(() -> new Provider[] {new WildFlyElytronProvider()});
        saslServerFactory = new FilterMechanismSaslServerFactory(saslServerFactory, (s) -> {
            AuthMechanism mechanism = toAuthMechanism("SASL", s);
            return mechanism != null && configurationMap.containsKey(mechanism);
        });
        saslServerFactory = new PropertiesSaslServerFactory(saslServerFactory, mechanismConfiguration);
        saslServerFactory = new SortedMechanismSaslServerFactory(saslServerFactory, SecurityRealmService::compare);

        saslBuilder.setFactory(saslServerFactory);
        saslBuilder.setMechanismConfigurationSelector((mi) -> {
            AuthMechanism mechanism = toAuthMechanism(mi.getMechanismType(), mi.getMechanismName());
            if (mechanism != null) {
                return configurationMap.get(mechanism);
            }
            return null;
        });
        saslAuthenticationFactory = saslBuilder.build();
    }

    private AuthMechanism toAuthMechanism(String mechanismType, String mechanismName) {
        switch (mechanismType) {
            case "SASL":
                switch (mechanismName) {
                    case "DIGEST-MD5":
                        return AuthMechanism.DIGEST;
                    case "JBOSS-LOCAL-USER":
                        return AuthMechanism.LOCAL;
                    case "PLAIN":
                        return AuthMechanism.PLAIN;
                }
                break;
            case "HTTP":
                switch (mechanismName) {
                    case "DIGEST":
                        return AuthMechanism.DIGEST;
                    case "BASIC":
                        return AuthMechanism.PLAIN;
                }
                break;
        }

        return null;
    }

    private static int compare(String nameOne, String nameTwo) {
        return toPriority(nameTwo) - toPriority(nameOne);
    }

    private static int toPriority(String name) {
        switch (name) {
            case "JBOSS-LOCAL-USER":
                return 10;
            default:
                return 0;
        }
    }

    private String getAuthDir(final String path) throws StartException {
        File authDir = new File(path, "auth");
        if (authDir.exists()) {
            if (!authDir.isDirectory()) {
                throw ROOT_LOGGER.unableToCreateTempDirForAuthTokensFileExists();
            }
        } else if (!authDir.mkdirs()) {
            //there is a race if multiple services are starting for the same
            //security realm
            if(!authDir.isDirectory()) {
                throw ROOT_LOGGER.unableToCreateAuthDir(authDir.getAbsolutePath());
            }
        } else {
            // As a precaution make perms user restricted for directories created (if the OS allows)
            authDir.setWritable(false, false);
            authDir.setWritable(true, true);
            authDir.setReadable(false, false);
            authDir.setReadable(true, true);
            authDir.setExecutable(false, false);
            authDir.setExecutable(true, true);
        }

        return authDir.getAbsolutePath();
    }


    public void stop(StopContext context) {
        ROOT_LOGGER.debugf("Stopping '%s' Security Realm Service", name);
        registeredServices.clear();
        saslAuthenticationFactory = null;
        httpAuthenticationFactory = null;
    }

    public SecurityRealmService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public String getName() {
        return name;
    }


    /*
     * SecurityRealm Methods
     */

    public Set<AuthMechanism> getSupportedAuthenticationMechanisms() {
        Set<AuthMechanism> response = new TreeSet<AuthMechanism>();
        response.addAll(registeredServices.keySet());
        return response;
    }

    public Map<String, String> getMechanismConfig(final AuthMechanism mechanism) {
        CallbackHandlerService service = getCallbackHandlerService(mechanism);

        return service.getConfigurationOptions();
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        for (CallbackHandlerService current : registeredServices.values()) {
            // As soon as one is ready for HTTP challenge based authentication return true.
            if (current.isReadyForHttpChallenge()) {
                return true;
            }
        }
        return false;
    }

    public AuthorizingCallbackHandler getAuthorizingCallbackHandler(AuthMechanism mechanism) {
        /*
         * The returned AuthorizingCallbackHandler is used for a single authentication request - this means that state can be
         * shared to combine the authentication step and the loading of authorization data.
         */
        final CallbackHandlerService handlerService = getCallbackHandlerService(mechanism);
        final Map<String, Object> sharedState = new HashMap<String, Object>();
        return new AuthorizingCallbackHandler() {
            CallbackHandler handler = handlerService.getCallbackHandler(sharedState);
            Map<String, String> options = handlerService.getConfigurationOptions();
            final boolean subjectCallbackSupported;

            {
                if (options.containsKey(SUBJECT_CALLBACK_SUPPORTED)) {
                    subjectCallbackSupported = Boolean.parseBoolean(options.get(SUBJECT_CALLBACK_SUPPORTED));
                } else {
                    subjectCallbackSupported = false;
                }
            }

            Subject subject;

            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                // For a follow up call just for AuthorizeCallback we don't want to insert a SubjectCallback
                if (subjectCallbackSupported && notAuthorizeCallback(callbacks)) {
                    Callback[] newCallbacks = new Callback[callbacks.length + 1];
                    System.arraycopy(callbacks, 0, newCallbacks, 0, callbacks.length);
                    SubjectCallback subjectCallBack = new SubjectCallback();
                    newCallbacks[newCallbacks.length - 1] = subjectCallBack;
                    handler.handle(newCallbacks);
                    subject = subjectCallBack.getSubject();
                } else {
                    handler.handle(callbacks);
                }
            }

            private boolean notAuthorizeCallback(Callback[] callbacks) {
                return (callbacks.length == 1 && callbacks[0] instanceof AuthorizeCallback) == false;
            }

            public SubjectUserInfo createSubjectUserInfo(Collection<Principal> userPrincipals) throws IOException {
                Subject subject = this.subject == null ? new Subject() : this.subject;
                Collection<Principal> allPrincipals = subject.getPrincipals();

                Principal ru = null;
                if (sharedState.containsKey(LOADED_USERNAME_KEY)) {
                    // If we need to modify the name ours gets priority.
                    ru = new RealmUser(getName(), (String) sharedState.get(LOADED_USERNAME_KEY));
                } else {
                    // Otherwise see if another already implements RealmUser
                    for (Principal userPrincipal : userPrincipals) {
                        if (userPrincipal instanceof RealmUser) {
                            ru = userPrincipal;
                            break;
                        }
                    }
                }

                for (Principal userPrincipal : userPrincipals) {
                    if (userPrincipal instanceof RealmUser == false) {
                        allPrincipals.add(userPrincipal);
                        if (ru == null) {
                            // Last resort map the first principal we find.
                            ru = new RealmUser(name, userPrincipal.getName());
                        }
                    }
                }

                if (ru != null) {
                    allPrincipals.add(ru);
                }

                Object skipGroupLoading = sharedState.get(SKIP_GROUP_LOADING_KEY);
                if (skipGroupLoading == null || Boolean.parseBoolean(skipGroupLoading.toString()) == false) {
                    SubjectSupplementalService subjectSupplementalService = subjectSupplemental.getOptionalValue();
                    if (subjectSupplementalService != null) {
                        SubjectSupplemental subjectSupplemental = subjectSupplementalService.getSubjectSupplemental(sharedState);
                        subjectSupplemental.supplementSubject(subject);
                    }

                    if (mapGroupsToRoles) {
                        Set<RealmGroup> groups = subject.getPrincipals(RealmGroup.class);
                        Set<RealmRole> roles = new HashSet<RealmRole>(groups.size());
                        for (RealmGroup current : groups) {
                            roles.add(new RealmRole(current.getName()));
                        }
                        subject.getPrincipals().addAll(roles);
                    }
                }

                return new RealmSubjectUserInfo(subject);
            }
        };
    }

    private CallbackHandlerService getCallbackHandlerService(final AuthMechanism mechanism) {
        if (registeredServices.containsKey(mechanism)) {
            return registeredServices.get(mechanism);
        }
        // As the service is started we do not expect any updates to the registry.

        // We didn't find a service that prefers this mechanism so now search for a service that also supports it.
        for (CallbackHandlerService current : registeredServices.values()) {
            if (current.getSupplementaryMechanisms().contains(mechanism)) {
                return current;
            }
        }

        throw DomainManagementLogger.ROOT_LOGGER.noCallbackHandlerForMechanism(mechanism.toString(), name);
    }

    @Override
    public SubjectIdentity getSubjectIdentity(String protocol, String forHost) {
        KeytabIdentityFactoryService kifs = keytabFactory.getOptionalValue();

        return kifs != null ? kifs.getSubjectIdentity(protocol, forHost) : null;
    }

    @Override
    public SaslAuthenticationFactory getSaslAuthenticationFactory() {
        return saslAuthenticationFactory;
    }

    @Override
    public HttpAuthenticationFactory getHttpAuthenticationFactory() {
        return httpAuthenticationFactory;
    }

    /*
     * Injectors
     */

    public InjectedValue<SubjectSupplementalService> getSubjectSupplementalInjector() {
        return subjectSupplemental;
    }

    public InjectedValue<SSLContext> getSSLContextInjector() {
        return sslContext;
    }

    public InjectedValue<CallbackHandlerFactory> getSecretCallbackFactory() {
        return secretCallbackFactory;
    }

    public InjectedValue<KeytabIdentityFactoryService> getKeytabIdentityFactoryInjector() {
        return keytabFactory;
    }

    public InjectedSetValue<CallbackHandlerService> getCallbackHandlerService() {
        return callbackHandlerServices;
    }

    public Injector<String> getTmpDirPathInjector() {
        return tmpDirPath;
    }

    public SSLContext getSSLContext() {
        return sslContext.getOptionalValue();
    }

    public CallbackHandlerFactory getSecretCallbackHandlerFactory() {
        return secretCallbackFactory.getOptionalValue();
    }

    static class SharedStateSecurityRealm implements org.wildfly.security.auth.server.SecurityRealm {

        private static ThreadLocal<Map<String, Object>> sharedStateLocal = new ThreadLocal<>();

        private final org.wildfly.security.auth.server.SecurityRealm wrapped;

        public SharedStateSecurityRealm(final org.wildfly.security.auth.server.SecurityRealm wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public RealmIdentity getRealmIdentity(IdentityLocator locator) throws RealmUnavailableException {
            try {
                sharedStateLocal.set(new HashMap<>());
                return wrapped.getRealmIdentity(locator);
            } finally {
                sharedStateLocal.remove();
            }
        }

        @Override
        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                throws RealmUnavailableException {
            return wrapped.getCredentialAcquireSupport(credentialType, algorithmName);
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return wrapped.getEvidenceVerifySupport(evidenceType, algorithmName);
        }

        static Map<String, Object> getSharedState() {
            return sharedStateLocal.get();
        }

    }
}
