/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import org.jboss.as.controller.services.path.PathManager;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.realm.JaasSecurityRealm;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.event.RealmEvent;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

import javax.security.auth.callback.CallbackHandler;
import java.io.File;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Map;
import java.util.function.Function;

import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

/**
 * Wrapper for JAAS REALM so it can be defined as a custom realm resource
 *
 * @deprecated Use a jaas-realm resource instead
 */
@Deprecated
public class JaasCustomSecurityRealmWrapper implements SecurityRealm {
    private JaasSecurityRealm jaasSecurityRealm;

    // receiving configuration from subsystem
    public void initialize(Map<String, String> configuration) throws StartException {

        String entry = configuration.get("entry");
        if (entry == null || entry.isEmpty()) {
            throw ROOT_LOGGER.jaasEntryNotDefined();
        }
        String pathParam = configuration.get("path");
        String relativeToParam = configuration.get("relative-to");
        String moduleNameParam = configuration.get("module");
        String callbackHandlerName = configuration.get("callbackHandlerName");

        String rootPath = null;
        FileAttributeDefinitions.PathResolver pathResolver;
        InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();
        if (pathParam != null) {
            pathResolver = pathResolver();
            File jaasConfigFile = pathResolver.path(pathParam).relativeTo(relativeToParam, pathManagerInjector.getOptionalValue()).resolve();
            if (!jaasConfigFile.exists()) {
                throw ROOT_LOGGER.jaasFileDoesNotExist(jaasConfigFile.getPath());
            }
            rootPath = jaasConfigFile.getPath();
        }

        CallbackHandler callbackhandler = null;
        ClassLoader classLoader;
        try {
            classLoader = doPrivileged((PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(moduleNameParam));
            if (callbackHandlerName != null) {
                Class<?> typeClazz = classLoader.loadClass(callbackHandlerName);
                callbackhandler = (CallbackHandler) typeClazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw ROOT_LOGGER.failedToLoadCallbackhandlerFromProvidedModule();
        }
        this.jaasSecurityRealm = new JaasSecurityRealm(entry, rootPath, classLoader, callbackhandler);
    }

    @Override
    public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
        return jaasSecurityRealm.getRealmIdentity(principal);
    }

    @Override
    public RealmIdentity getRealmIdentity(Evidence evidence) throws RealmUnavailableException {
        return jaasSecurityRealm.getRealmIdentity(evidence);
    }

    @Override
    public RealmIdentity getRealmIdentity(Evidence evidence, Function<Principal, Principal> principalTransformer) throws RealmUnavailableException {
        return jaasSecurityRealm.getRealmIdentity(evidence, principalTransformer);
    }

    @Override
    public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName) throws RealmUnavailableException {
        return jaasSecurityRealm.getCredentialAcquireSupport(credentialType, algorithmName);
    }

    @Override
    public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
        return jaasSecurityRealm.getCredentialAcquireSupport(credentialType, algorithmName, parameterSpec);
    }

    @Override
    public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName) throws RealmUnavailableException {
        return jaasSecurityRealm.getEvidenceVerifySupport(evidenceType, algorithmName);
    }

    @Override
    public void handleRealmEvent(RealmEvent event) {
        jaasSecurityRealm.handleRealmEvent(event);
    }
}