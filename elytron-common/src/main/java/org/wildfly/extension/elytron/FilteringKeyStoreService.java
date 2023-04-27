/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron._private.ElytronCommonMessages.ROOT_LOGGER;

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
