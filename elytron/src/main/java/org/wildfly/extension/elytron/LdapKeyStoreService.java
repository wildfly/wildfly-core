/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.capabilities._private.DirContextSupplier;
import org.wildfly.security.keystore.LdapKeyStore;
import org.wildfly.security.keystore.UnmodifiableKeyStore;

/**
 * A {@link Service} responsible for a single {@link LdapKeyStore} instance.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
class LdapKeyStoreService implements ModifiableKeyStoreService {

    private final InjectedValue<DirContextSupplier> dirContextSupplierInjector = new InjectedValue<>();

    private final String searchPath;
    private final String filterAlias;
    private final String filterCertificate;
    private final String filterIterate;

    private final LdapName createPath;
    private final String createRdn;
    private final Attributes createAttributes;

    private final String aliasAttribute;
    private final String certificateAttribute;
    private final String certificateType;
    private final String certificateChainAttribute;
    private final String certificateChainEncoding;
    private final String keyAttribute;
    private final String keyType;

    private volatile KeyStore modifiableKeyStore = null;
    private volatile KeyStore unmodifiableKeyStore = null;

    LdapKeyStoreService(String searchPath, String filterAlias, String filterCertificate,
                        String filterIterate, LdapName createPath, String createRdn, Attributes createAttributes,
                        String aliasAttribute, String certificateAttribute, String certificateType,
                        String certificateChainAttribute, String certificateChainEncoding,
                        String keyAttribute, String keyType) {
        this.searchPath = searchPath;
        this.filterAlias = filterAlias;
        this.filterCertificate = filterCertificate;
        this.filterIterate = filterIterate;
        this.createPath = createPath;
        this.createRdn = createRdn;
        this.createAttributes = createAttributes;
        this.aliasAttribute = aliasAttribute;
        this.certificateAttribute = certificateAttribute;
        this.certificateType = certificateType;
        this.certificateChainAttribute = certificateChainAttribute;
        this.certificateChainEncoding = certificateChainEncoding;
        this.keyAttribute = keyAttribute;
        this.keyType = keyType;
    }

    Injector<DirContextSupplier> getDirContextSupplierInjector() {
        return dirContextSupplierInjector;
    }

    /*
     * Service Lifecycle Related Methods
     */

    @Override
    public void start(StartContext startContext) throws StartException {
        try {
            LdapKeyStore.Builder builder = LdapKeyStore.builder()
                    .setDirContextSupplier(dirContextSupplierInjector.getValue())
                    .setSearchPath(searchPath);

            if (filterAlias != null) builder.setFilterAlias(filterAlias);
            if (filterCertificate != null) builder.setFilterCertificate(filterCertificate);
            if (filterIterate != null) builder.setFilterIterate(filterIterate);
            if (createPath != null) builder.setCreatePath(createPath);
            if (createRdn != null) builder.setCreateRdn(createRdn);
            if (createAttributes != null) builder.setCreateAttributes(createAttributes);
            if (aliasAttribute != null) builder.setAliasAttribute(aliasAttribute);
            if (certificateAttribute != null) builder.setCertificateAttribute(certificateAttribute);
            if (certificateType != null) builder.setCertificateType(certificateType);
            if (certificateChainAttribute != null) builder.setCertificateChainAttribute(certificateChainAttribute);
            if (certificateChainEncoding != null) builder.setCertificateChainEncoding(certificateChainEncoding);
            if (keyAttribute != null) builder.setKeyAttribute(keyAttribute);
            if (keyType != null) builder.setKeyType(keyType);

            KeyStore keyStore = builder.build();
            keyStore.load(null); // initialize
            this.modifiableKeyStore = keyStore;
            this.unmodifiableKeyStore = UnmodifiableKeyStore.unmodifiableKeyStore(keyStore);
        } catch (GeneralSecurityException | IOException e) {
            throw ROOT_LOGGER.unableToStartService(e);
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        this.modifiableKeyStore = null;
        this.unmodifiableKeyStore = null;
    }

    @Override
    public KeyStore getValue() throws IllegalStateException, IllegalArgumentException {
        return unmodifiableKeyStore;
    }

    public KeyStore getModifiableValue() {
        return modifiableKeyStore;
    }
}