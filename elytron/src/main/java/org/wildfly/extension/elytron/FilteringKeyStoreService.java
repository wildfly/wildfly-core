/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.KeyStore;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.keystore.AliasFilter;
import org.wildfly.security.keystore.FilteringKeyStore;
import org.wildfly.security.keystore.UnmodifiableKeyStore;

/**
 * A {@link Service} responsible for a single {@link KeyStore} instance.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
class FilteringKeyStoreService implements ModifiableKeyStoreService {

    final InjectedValue<KeyStore> keyStoreInjector;
    final String aliasFilter;
    KeyStore filteringKeyStore;
    KeyStore modifiableFilteringKeyStore;

    FilteringKeyStoreService(InjectedValue<KeyStore> keyStoreInjector, String aliasFilter) {
        this.keyStoreInjector = keyStoreInjector;
        this.aliasFilter = aliasFilter;
    }

    /*
     * Service Lifecycle Related Methods
     */

    @Override
    public void start(StartContext startContext) throws StartException {
        try {
            KeyStore keyStore = keyStoreInjector.getValue();
            AliasFilter filter = AliasFilter.fromString(aliasFilter);
            KeyStore unmodifiable = UnmodifiableKeyStore.unmodifiableKeyStore(keyStore);
            KeyStore modifiable = keyStore;

            ROOT_LOGGER.tracef(
                    "starting:  aliasFilter = %s  filter = %s  unmodifiable = %s  modifiable = %s",
                    aliasFilter, filter, unmodifiable, modifiable);

            filteringKeyStore = FilteringKeyStore.filteringKeyStore(unmodifiable, filter);
            if (modifiableFilteringKeyStore == null) {
                modifiableFilteringKeyStore = FilteringKeyStore.filteringKeyStore(modifiable, filter);
            }
        } catch (Exception e) {
            throw new StartException(e);
        }
    }

    @Override
    public void stop(StopContext stopContext) {

        ROOT_LOGGER.tracef(
                "stopping:  filteringKeyStore = %s  modifiableFilteringKeyStore = %s",
                filteringKeyStore, modifiableFilteringKeyStore
        );

        filteringKeyStore = null;
        modifiableFilteringKeyStore = null;
    }

    @Override
    public KeyStore getValue() throws IllegalStateException, IllegalArgumentException {
        return filteringKeyStore;
    }

    public KeyStore getModifiableValue() {
        if (modifiableFilteringKeyStore == null) {
            throw new UnsupportedOperationException();
        }
        return modifiableFilteringKeyStore;
    }
}
