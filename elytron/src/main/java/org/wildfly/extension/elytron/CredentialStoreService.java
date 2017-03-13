/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.util.Map;

import org.jboss.as.controller.security.CredentialStoreURIParser;
import org.jboss.as.controller.services.path.PathEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManager.Callback.Handle;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;

/**
 * A {@link Service} responsible for a {@link CredentialStore} instance.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 */
class CredentialStoreService implements Service<CredentialStore> {

    // generally supported credential store attributes
    private static final String CS_LOCATION_ATTRIBUTE = "location";

    // KeyStore backed credential store supported attributes
    private static final String CS_KEY_STORE_TYPE_ATTRIBUTE = "keyStoreType";

    private CredentialStore credentialStore;
    private final String type;
    private final String provider;
    private final String providerLoaderName;
    private final String otherProvidersLoaderName;
    private final String relativeTo;
    private final String location;
    private final String name;
    private final Map<String, String> credentialStoreAttributes;

    private final InjectedValue<PathManager> pathManager = new InjectedValue<>();
    private final InjectedValue<Provider[]> providers = new InjectedValue<>();
    private final InjectedValue<Provider[]> otherProviders = new InjectedValue<>();
    private final InjectedValue<CredentialStore> injectedCredentialStore = new InjectedValue<>();
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplier = new InjectedValue<>();

    private Handle callbackHandle;

    private CredentialStoreService(String name, Map<String, String> credentialStoreAttributes, String type, String provider, String relativeTo, String location, String providerLoaderName, String otherProvidersLoaderName) throws CredentialStoreException {
        this.name = name;
        this.type = type != null ? type : KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE;
        this.provider = provider;
        this.relativeTo = relativeTo;
        this.credentialStoreAttributes = credentialStoreAttributes;
        this.location = location;
        this.providerLoaderName = providerLoaderName;
        this.otherProvidersLoaderName = otherProvidersLoaderName;
    }

    static CredentialStoreService createCredentialStoreService(String name, String uri, String type, String provider, String relativeTo, String providerLoaderName, String keyStoreProvidersLoaderName) throws CredentialStoreException {
        try {
            CredentialStoreURIParser credentialStoreURIParser = new CredentialStoreURIParser(uri);
            String nameToSet = name != null ? name : credentialStoreURIParser.getName(); // once we specify name, the name from uri is ignored
            Map<String, String> credentialStoreAttributes = credentialStoreURIParser.getOptionsMap();
            credentialStoreAttributes.put(ElytronDescriptionConstants.CREDENTIAL_STORE_NAME, nameToSet);
            credentialStoreAttributes.putIfAbsent(CS_KEY_STORE_TYPE_ATTRIBUTE, "JCEKS");
            String storageFile = credentialStoreURIParser.getStorageFile();
            return new CredentialStoreService(nameToSet, credentialStoreAttributes, type, provider, relativeTo, storageFile != null ? storageFile : name, providerLoaderName, keyStoreProvidersLoaderName);
        } catch (URISyntaxException e) {
            throw new CredentialStoreException(e);
        }
    }

    /*
     * Service Lifecycle Related Methods
     */

    @Override
    public void start(StartContext startContext) throws StartException {
        Path loc = resolveLocation();
        try {
            credentialStoreAttributes.put(CS_LOCATION_ATTRIBUTE, loc.toAbsolutePath().toString());
            credentialStore = getCredentialStoreInstance();
            credentialStore.initialize(credentialStoreAttributes, resolveCredentialStoreProtectionParameter(), otherProviders.getOptionalValue());
        } catch (Exception e) {
            throw ElytronSubsystemMessages.ROOT_LOGGER.unableToStartService(e);
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        if (callbackHandle != null) {
            callbackHandle.remove();
        }
    }

    @Override
    public CredentialStore getValue() {
        return credentialStore;
    }

    private Path resolveLocation() {
        if (relativeTo != null) {
            PathManager pathManager = this.pathManager.getValue();
            String baseDir = pathManager.resolveRelativePathEntry("", relativeTo);
            callbackHandle = pathManager.registerCallback(relativeTo, new PathManager.Callback() {

                @Override
                public void pathModelEvent(PathManager.PathEventContext eventContext, String name) {
                    if (eventContext.isResourceServiceRestartAllowed() == false) {
                        eventContext.reloadRequired();
                    }
                }

                @Override
                public void pathEvent(PathManager.Event event, PathEntry pathEntry) {
                    // Service dependencies should trigger a stop and start.
                }
            }, PathManager.Event.REMOVED, PathManager.Event.UPDATED);
            return Paths.get(baseDir, location);
        } else {
            return Paths.get(location);
        }
    }

    private CredentialStore getCredentialStoreInstance() throws CredentialStoreException, NoSuchAlgorithmException, NoSuchProviderException {
        if (provider != null) {
            // directly specified provider
            return CredentialStore.getInstance(type, provider);
        }

        Provider[] injectedProviders = providers.getOptionalValue();
        if (injectedProviders != null) {
            // injected provider list, select the first provider with corresponding type
            for (Provider p : injectedProviders) {
                try {
                    return CredentialStore.getInstance(type, p);
                } catch (NoSuchAlgorithmException ignore) {
                }
            }
            throw ROOT_LOGGER.providerLoaderCannotSupplyProvider(providerLoaderName, type);
        } else {
            // default provider
            return CredentialStore.getInstance(type);
        }
    }

    Injector<Provider[]> getProvidersInjector() {
        return providers;
    }

    Injector<Provider[]> getOtherProvidersInjector() {
        return otherProviders;
    }

    Injector<PathManager> getPathManagerInjector() {
        return pathManager;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getProvider() {
        return provider;
    }

    Injector<CredentialStore> getCredentialStoreInjector() {
        return injectedCredentialStore;
    }

    Injector<ExceptionSupplier<CredentialSource, Exception>> getCredentialSourceSupplierInjector() {
        return credentialSourceSupplier;
    }

    private CredentialStore.CredentialSourceProtectionParameter resolveCredentialStoreProtectionParameter() throws Exception {
        ExceptionSupplier<CredentialSource, Exception> sourceSupplier = credentialSourceSupplier.getValue();
        CredentialSource cs = sourceSupplier != null ? sourceSupplier.get() : null;
        if (cs != null) {
            return credentialToCredentialSourceProtectionParameter(cs.getCredential(PasswordCredential.class));
        } else {
            throw ROOT_LOGGER.credentialStoreProtectionParameterCannotBeResolved(name);
        }
    }

    private CredentialStore.CredentialSourceProtectionParameter credentialToCredentialSourceProtectionParameter(Credential credential) {
        return new CredentialStore.CredentialSourceProtectionParameter(IdentityCredentials.NONE.withCredential(credential));
    }

}