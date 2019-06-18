/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
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
import org.wildfly.security.credential.source.CredentialSource;
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
    private final String certificateAuthorityName;
    private final List<String> contactUrlsList;
    private final String alias;
    private final String keyStoreName;
    private volatile AcmeAccount acmeAccount;

    AcmeAccountService(String certificateAuthorityName, List<String> contactUrlsList, String alias, String keyStoreName) {
        this.certificateAuthorityName = certificateAuthorityName;
        this.contactUrlsList = contactUrlsList;
        this.alias = alias;
        this.keyStoreName = keyStoreName;
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

    char[] resolveKeyPassword(KeyStoreService keyStoreService) throws RuntimeException {
        try {
            return keyStoreService.resolveKeyPassword(credentialSourceSupplierInjector.getOptionalValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
