/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.x500.cert.acme.AcmeAccount;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;

/**
 * A {@link Service} responsible for a single {@link AcmeAccount} instance.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
class AcmeAccountService implements Service<AcmeAccount> {

    private final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> credentialSourceSupplierInjector = new InjectedValue<>();
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> externalAccountBindingCredentialSourceSupplierInjector = new InjectedValue<>();
    private final String certificateAuthorityName;
    private final List<String> contactUrlsList;
    private final String alias;
    private final String keyStoreName;
    private final String externalAccountBindingKeyId;
    private volatile AcmeAccount acmeAccount;

    AcmeAccountService(String certificateAuthorityName, List<String> contactUrlsList, String alias, String keyStoreName,
            String externalAccountBindingKeyId) {
        this.certificateAuthorityName = certificateAuthorityName;
        this.contactUrlsList = contactUrlsList;
        this.alias = alias;
        this.keyStoreName = keyStoreName;
        this.externalAccountBindingKeyId = externalAccountBindingKeyId;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        try {
            final ServiceRegistry serviceRegistry = startContext.getController().getServiceContainer();
            final ModifiableKeyStoreService keyStoreService = CertificateAuthorityAccountDefinition.getModifiableKeyStoreService(serviceRegistry, keyStoreName);
            char[] keyPassword = resolveKeyPassword((KeyStoreService) keyStoreService);
            KeyStore keyStore = keyStoreInjector.getValue();
            CertificateAuthority certificateAuthority;
            if (certificateAuthorityName.equalsIgnoreCase(CertificateAuthority.LETS_ENCRYPT.getName())) {
                certificateAuthority = CertificateAuthority.LETS_ENCRYPT;
            } else {
                certificateAuthority = CertificateAuthorityDefinition.getCertificateAuthorityService(serviceRegistry, certificateAuthorityName).getValue();
            }

            AcmeAccount.Builder acmeAccountBuilder = AcmeAccount.builder()
                    .setServerUrl(certificateAuthority.getUrl());
            if (certificateAuthority.getStagingUrl() != null) {
                acmeAccountBuilder.setStagingServerUrl(certificateAuthority.getStagingUrl());
            }
            if (! contactUrlsList.isEmpty()) {
                acmeAccountBuilder = acmeAccountBuilder.setContactUrls(contactUrlsList.toArray(new String[contactUrlsList.size()]));
            }
            boolean updateAccountKeyStore = false;
            if (keyStore.containsAlias(alias)) {
                // use existing account key pair
                X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
                if (certificate == null) {
                    throw ROOT_LOGGER.unableToObtainCertificateAuthorityAccountCertificate(alias);
                }
                PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword);
                if (privateKey == null) {
                    throw ROOT_LOGGER.unableToObtainCertificateAuthorityAccountPrivateKey(alias);
                }
                acmeAccountBuilder = acmeAccountBuilder.setKey(certificate, privateKey);
            } else {
                updateAccountKeyStore = true;
            }
            if (externalAccountBindingKeyId != null) {
                acmeAccountBuilder.setExternalAccountBinding(externalAccountBindingKeyId, resolveExternalAccountBindingHmacKey());
            }
            acmeAccount = acmeAccountBuilder.build();
            if (updateAccountKeyStore) {
                saveCertificateAuthorityAccountKey(keyStoreService, keyPassword); // persist the generated key pair
            }
        } catch (Exception e) {
            throw ROOT_LOGGER.unableToStartService(e);
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        acmeAccount = null;
    }

    @Override
    public AcmeAccount getValue() throws IllegalStateException, IllegalArgumentException {
        return acmeAccount;
    }

    Injector<KeyStore> getKeyStoreInjector() {
        return keyStoreInjector;
    }

    Injector<ExceptionSupplier<CredentialSource, Exception>> getCredentialSourceSupplierInjector() {
        return credentialSourceSupplierInjector;
    }

    Injector<ExceptionSupplier<CredentialSource, Exception>> getExternalAccountBindingCredentialSourceSupplierInjector() {
        return externalAccountBindingCredentialSourceSupplierInjector;
    }

    char[] resolveKeyPassword(KeyStoreService keyStoreService) throws RuntimeException {
        try {
            return keyStoreService.resolveKeyPassword(credentialSourceSupplierInjector.getOptionalValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String resolveExternalAccountBindingHmacKey() throws Exception {
        ExceptionSupplier<CredentialSource, Exception> sourceSupplier = externalAccountBindingCredentialSourceSupplierInjector.getValue();
        CredentialSource credentialSource = sourceSupplier != null ? sourceSupplier.get() : null;
        if (credentialSource == null) {
            throw ROOT_LOGGER.credentialCannotBeResolved();
        }
        PasswordCredential credential = credentialSource.getCredential(PasswordCredential.class);
        if (credential == null) {
            throw ROOT_LOGGER.credentialCannotBeResolved();
        }
        ClearPassword password = credential.getPassword(ClearPassword.class);
        if (password == null) {
            throw ROOT_LOGGER.credentialCannotBeResolved();
        }
        return new String(password.getPassword());
    }

    void saveCertificateAuthorityAccountKey(OperationContext operationContext) throws OperationFailedException {
        final ModifiableKeyStoreService keyStoreService = CertificateAuthorityAccountDefinition.getModifiableKeyStoreService(operationContext, keyStoreName);
        char[] keyPassword = resolveKeyPassword((KeyStoreService) keyStoreService);
        saveCertificateAuthorityAccountKey(keyStoreService, keyPassword);
    }

    private void saveCertificateAuthorityAccountKey(ModifiableKeyStoreService keyStoreService, char[] keyPassword) throws OperationFailedException {
        KeyStore modifiableAccountkeyStore = keyStoreService.getModifiableValue();
        try {
            modifiableAccountkeyStore.setKeyEntry(alias, acmeAccount.getPrivateKey(), keyPassword, new X509Certificate[]{ acmeAccount.getCertificate() });
        } catch (KeyStoreException e) {
            throw ROOT_LOGGER.unableToUpdateCertificateAuthorityAccountKeyStore(e, e.getLocalizedMessage());
        }
        ((KeyStoreService) keyStoreService).save();
    }
}
