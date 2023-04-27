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

import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron._private.ElytronCommonMessages.ROOT_LOGGER;
import static org.wildfly.security.provider.util.ProviderUtil.findProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Enumeration;
import java.util.TimeZone;
import java.util.function.Supplier;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.security.EmptyProvider;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.keystore.AliasFilter;
import org.wildfly.security.keystore.AtomicLoadKeyStore;
import org.wildfly.security.keystore.FilteringKeyStore;
import org.wildfly.security.keystore.KeyStoreUtil;
import org.wildfly.security.keystore.ModifyTrackingKeyStore;
import org.wildfly.security.keystore.UnmodifiableKeyStore;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;

/**
 * A {@link Service} responsible for a single {@link KeyStore} instance.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class KeyStoreService implements ModifiableKeyStoreService {

    private static final String GENERATED_CERTIFICATE_ALIAS = "server";
    private static final String GENERATED_CERTIFICATE_KEY_ALGORITHM = "RSA";
    private static final int GENERATED_CERTIFICATE_KEY_SIZE = 2048;
    private static final String GENERATED_CERTIFICATE_SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int HEX_DELIMITER = ':';
    private static final String COMMON_NAME_PREFIX = "CN=";

    private final String provider;
    private final String type;
    private final String path;
    private final String relativeTo;
    private final boolean required;
    private final String aliasFilter;

    private final InjectedValue<PathManager> pathManager = new InjectedValue<>();
    private final InjectedValue<Provider[]> providers = new InjectedValue<>();
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplier = new InjectedValue<>();

    private PathResolver pathResolver;
    private File resolvedPath;

    private volatile long synched;
    private volatile AtomicLoadKeyStore keyStore = null;
    private volatile ModifyTrackingKeyStore trackingKeyStore = null;
    private volatile KeyStore unmodifiableKeyStore = null;

    private KeyStoreService(String provider, String type, String relativeTo, String path, boolean required, String aliasFilter) {
        this.provider = provider;
        this.type = type;
        this.relativeTo = relativeTo;
        this.path = path;
        this.required = required;
        this.aliasFilter = aliasFilter;
    }

    static KeyStoreService createFileLessKeyStoreService(String provider, String type, String aliasFilter) {
        return new KeyStoreService(provider, type, null, null, false, aliasFilter);
    }

    static KeyStoreService createFileBasedKeyStoreService(String provider, String type, String relativeTo, String path, boolean required, String aliasFilter) {
        return new KeyStoreService(provider, type, relativeTo, path, required, aliasFilter);
    }

    /*
     * Service Lifecycle Related Methods
     */

    @Override
    public void start(StartContext startContext) throws StartException {
        try {
            AtomicLoadKeyStore keyStore = null;

            if (type != null) {
                Provider provider = resolveProvider();
                keyStore = AtomicLoadKeyStore.newInstance(type, provider);
            }

            if (path != null) {
                pathResolver = pathResolver();
                resolvedPath = getResolvedPath(pathResolver, path, relativeTo);
            }

            synched = System.currentTimeMillis();
            if (resolvedPath != null && ! resolvedPath.exists()) {
                if (required) {
                    if (type == null) {
                        throw ROOT_LOGGER.nonexistingKeyStoreMissingType();
                    } else {
                        throw ROOT_LOGGER.keyStoreFileNotExists(resolvedPath.getAbsolutePath());
                    }
                } else {
                    ROOT_LOGGER.keyStoreFileNotExistsButIgnored(resolvedPath.getAbsolutePath());
                }
            }

            try (FileInputStream is = (resolvedPath != null && resolvedPath.exists()) ? new FileInputStream(resolvedPath) : null) {
                char[] password = resolvePassword();

                ROOT_LOGGER.tracef(
                        "starting:  type = %s  provider = %s  path = %s  resolvedPath = %s  password = %b  aliasFilter = %s",
                        type, provider, path, resolvedPath, password != null, aliasFilter
                );

                if (is != null) {
                    if (type != null) {
                        keyStore.load(is, password);
                    } else {
                        Provider[] resolvedProviders = providers.getOptionalValue();
                        if (resolvedProviders == null) {
                            resolvedProviders = Security.getProviders();
                        }
                        final Provider[] finalProviders = resolvedProviders;
                        KeyStore detected = KeyStoreUtil.loadKeyStore(() -> finalProviders, this.provider, is, resolvedPath.getPath(), password);

                        if (detected == null) {
                            throw ROOT_LOGGER.unableToDetectKeyStore(resolvedPath.getPath());
                        }

                        keyStore = AtomicLoadKeyStore.atomize(detected);
                    }
                } else {
                    if (keyStore == null) {
                        String defaultType = KeyStore.getDefaultType();
                        ROOT_LOGGER.debugf(
                                "KeyStore: provider = %s  path = %s  resolvedPath = %s  password = %b  aliasFilter = %s does not exist. New keystore of %s type will be created.",
                                provider, path, resolvedPath, password != null, aliasFilter, defaultType
                        );
                        keyStore = AtomicLoadKeyStore.newInstance(defaultType);
                    }

                    synchronized (EmptyProvider.getInstance()) {
                        keyStore.load(null, password);
                    }
                }
                checkCertificatesValidity(keyStore);
            }

            this.keyStore = keyStore;
            KeyStore intermediate = aliasFilter != null ? FilteringKeyStore.filteringKeyStore(keyStore, AliasFilter.fromString(aliasFilter)) :  keyStore;
            this.trackingKeyStore = ModifyTrackingKeyStore.modifyTrackingKeyStore(intermediate);
            this.unmodifiableKeyStore = UnmodifiableKeyStore.unmodifiableKeyStore(intermediate);
        } catch (Exception e) {
            throw ROOT_LOGGER.unableToStartService(e);
        }
    }

    private Provider resolveProvider() throws StartException {
        Provider[] candidates = providers.getOptionalValue();
        Supplier<Provider[]> providersSupplier = () -> candidates == null ? Security.getProviders() : candidates;
        Provider identified = findProvider(providersSupplier, provider, KeyStore.class, type);
        if (identified == null) {
            throw ROOT_LOGGER.noSuitableProvider(type);
        }
        return identified;
    }

    private AtomicLoadKeyStore.LoadKey load(AtomicLoadKeyStore keyStore) throws Exception {
        try (InputStream is = resolvedPath != null ? new FileInputStream(resolvedPath) : null) {
            AtomicLoadKeyStore.LoadKey loadKey = keyStore.revertibleLoad(is, resolvePassword());
            checkCertificatesValidity(keyStore);
            return loadKey;
        }
    }

    private void checkCertificatesValidity(KeyStore keyStore) throws KeyStoreException {
        if (ROOT_LOGGER.isEnabled(Logger.Level.WARN)) {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate certificate = keyStore.getCertificate(alias);
                if (certificate != null && certificate instanceof X509Certificate) {
                    try {
                        ((X509Certificate) certificate).checkValidity();
                    } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                        ROOT_LOGGER.certificateNotValid(alias, e);
                    }
                }
            }
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        ROOT_LOGGER.tracef(
                "stopping:  keyStore = %s  unmodifiableKeyStore = %s  trackingKeyStore = %s  pathResolver = %s",
                keyStore, unmodifiableKeyStore, trackingKeyStore, pathResolver
        );
        keyStore = null;
        unmodifiableKeyStore = null;
        trackingKeyStore = null;
        if (pathResolver != null) {
            pathResolver.clear();
            pathResolver = null;
        }
    }

    @Override
    public KeyStore getValue() throws IllegalStateException, IllegalArgumentException {
        return unmodifiableKeyStore;
    }

    public KeyStore getModifiableValue() {
        return trackingKeyStore;
    }

    Injector<PathManager> getPathManagerInjector() {
        return pathManager;
    }

    Injector<Provider[]> getProvidersInjector() {
        return providers;
    }

    Injector<ExceptionSupplier<CredentialSource, Exception>> getCredentialSourceSupplierInjector() {
        return credentialSourceSupplier;
    }

    String getResolvedAbsolutePath() {
        return resolvedPath != null ? resolvedPath.getAbsolutePath() : null;
    }

    /*
     * OperationStepHandler Access Methods
     */

    long timeSynched() {
        return synched;
    }

    LoadKey load() throws OperationFailedException {
        try {
            ROOT_LOGGER.tracef("reloading KeyStore from file [%s]", resolvedPath);
            AtomicLoadKeyStore.LoadKey loadKey = load(keyStore);
            long originalSynced = synched;
            synched = System.currentTimeMillis();
            boolean originalModified = trackingKeyStore.isModified();
            trackingKeyStore.setModified(false);
            return new LoadKey(loadKey, originalSynced, originalModified);
        } catch (Exception e) {
            throw ROOT_LOGGER.unableToCompleteOperation(e, e.getLocalizedMessage());
        }
    }

    void revertLoad(final LoadKey loadKey) {
        ROOT_LOGGER.trace("reverting load of KeyStore");
        keyStore.revert(loadKey.loadKey);
        synched = loadKey.modifiedTime;
        trackingKeyStore.setModified(loadKey.modified);
    }

    void save() throws OperationFailedException {
        if (resolvedPath == null) {
            throw ROOT_LOGGER.cantSaveWithoutFile(path);
        }
        ROOT_LOGGER.tracef("saving KeyStore to the file [%s]", resolvedPath);
        try (FileOutputStream fos = new FileOutputStream(resolvedPath)) {
            keyStore.store(fos, resolvePassword());
            synched = System.currentTimeMillis();
            trackingKeyStore.setModified(false);
        } catch (Exception e) {
            throw ROOT_LOGGER.unableToCompleteOperation(e, e.getLocalizedMessage());
        }
    }

    boolean isModified() {
        return trackingKeyStore.isModified();
    }

    char[] resolveKeyPassword(final ExceptionSupplier<CredentialSource, Exception> keyPasswordCredentialSourceSupplier) throws Exception {
        if (keyPasswordCredentialSourceSupplier == null) {
            // use the key-store password if no key password is provided
            return resolvePassword();
        }
        CredentialSource cs = keyPasswordCredentialSourceSupplier.get();
        String path = resolvedPath != null ? resolvedPath.getPath() : "null";
        if (cs == null) throw ROOT_LOGGER.keyPasswordCannotBeResolved(path);
        PasswordCredential credential = cs.getCredential(PasswordCredential.class);
        if (credential == null) throw ROOT_LOGGER.keyPasswordCannotBeResolved(path);
        ClearPassword password = credential.getPassword(ClearPassword.class);
        if (password == null) throw ROOT_LOGGER.keyPasswordCannotBeResolved(path);
        return password.getPassword();
    }

    private char[] resolvePassword() throws Exception {
        ExceptionSupplier<CredentialSource, Exception> sourceSupplier = credentialSourceSupplier.getValue();
        CredentialSource cs = sourceSupplier != null ? sourceSupplier.get() : null;
        String path = resolvedPath != null ? resolvedPath.getPath() : "null";
        if (cs == null) throw ROOT_LOGGER.keyStorePasswordCannotBeResolved(path);
        PasswordCredential credential = cs.getCredential(PasswordCredential.class);
        if (credential == null) throw ROOT_LOGGER.keyStorePasswordCannotBeResolved(path);
        ClearPassword password = credential.getPassword(ClearPassword.class);
        if (password == null) throw ROOT_LOGGER.keyStorePasswordCannotBeResolved(path);

        return password.getPassword();
    }

    File getResolvedPath(PathResolver pathResolver, String path, String relativeTo) {
        pathResolver.path(path);
        if (relativeTo != null) {
            pathResolver.relativeTo(relativeTo, pathManager.getValue());
        }
        return pathResolver.resolve();
    }

    void generateAndSaveSelfSignedCertificate(String host, char[] password) {
        try {
            if (shouldAutoGenerateSelfSignedCertificate(host)) {
                // generate certificate
                Date from = new Date();
                Date to = new Date(from.getTime() + (1000L * 60L * 60L * 24L * 365L * 10L));
                SelfSignedX509CertificateAndSigningKey selfSignedCertificateAndSigningKey = SelfSignedX509CertificateAndSigningKey.builder()
                        .setDn(new X500Principal(COMMON_NAME_PREFIX + host))
                        .setNotValidAfter(ZonedDateTime.ofInstant(Instant.ofEpochMilli(to.getTime()), TimeZone.getDefault().toZoneId()))
                        .setNotValidBefore(ZonedDateTime.ofInstant(Instant.ofEpochMilli(from.getTime()), TimeZone.getDefault().toZoneId()))
                        .setKeyAlgorithmName(GENERATED_CERTIFICATE_KEY_ALGORITHM)
                        .setKeySize(GENERATED_CERTIFICATE_KEY_SIZE)
                        .setSignatureAlgorithmName(GENERATED_CERTIFICATE_SIGNATURE_ALGORITHM)
                        .build();
                X509Certificate selfSignedCertificate = selfSignedCertificateAndSigningKey.getSelfSignedCertificate();
                keyStore.setKeyEntry(GENERATED_CERTIFICATE_ALIAS, selfSignedCertificateAndSigningKey.getSigningKey(), password == null ? resolvePassword() : password,
                        new X509Certificate[]{selfSignedCertificate});
                ROOT_LOGGER.selfSignedCertificateHasBeenCreated(resolvedPath.getAbsolutePath(), getShaFingerprint(selfSignedCertificate, "SHA-1"), getShaFingerprint(selfSignedCertificate, "SHA-256"));
                save();
            }
        } catch (Exception e) {
            throw ROOT_LOGGER.failedToStoreGeneratedSelfSignedCertificate(e);
        }
    }

    boolean shouldAutoGenerateSelfSignedCertificate(String host) {
        return host != null && resolvedPath != null && ! resolvedPath.exists();
    }

    private static String getShaFingerprint(X509Certificate certificate, String algorithm) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(certificate.getEncoded());
        byte[] digest = md.digest();
        return ByteIterator.ofBytes(digest).hexEncode().drainToString(HEX_DELIMITER, 2);
    }

    static class LoadKey {
        private final AtomicLoadKeyStore.LoadKey loadKey;
        private final long modifiedTime;
        private final boolean modified;

        LoadKey(AtomicLoadKeyStore.LoadKey loadKey, long modifiedTime, boolean modified) {
            this.loadKey = loadKey;
            this.modifiedTime = modifiedTime;
            this.modified = modified;
        }
    }

}
