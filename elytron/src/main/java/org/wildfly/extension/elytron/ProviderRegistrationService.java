/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Core {@link Service} for the Elytron subsystem handling {@link Provider} registration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ProviderRegistrationService implements Service<Void> {

    static final ServiceName SERVICE_NAME = ElytronExtension.BASE_SERVICE_NAME.append(ElytronDescriptionConstants.PROVIDER_REGISTRATION);

    private InjectedValue<Provider[]> initialProviders = new InjectedValue<>();
    private InjectedValue<Provider[]> finalProviders = new InjectedValue<>();
    private final Set<String> providersToRemove;

    private final Set<String> registeredProviderNames = new HashSet<>();

    ProviderRegistrationService(Collection<String> providersToRemove) {
        this.providersToRemove = new HashSet<>(providersToRemove == null ? Collections.emptySet() : providersToRemove);
    }

    @Override
    public void start(StartContext context) throws StartException {
        SecurityActions.doPrivileged((PrivilegedAction<Void>) () -> {
            for (String s : providersToRemove) {
                Security.removeProvider(s);
            }
            return null;
        });

        final Provider[] initialProviders = this.initialProviders.getOptionalValue();
        final Provider[] finalProviders = this.finalProviders.getOptionalValue();

        if (initialProviders != null) {
            SecurityActions.doPrivileged((PrivilegedAction<Void>) () -> {
                for (int i = initialProviders.length - 1; i >= 0; i--) {
                    int position = Security.insertProviderAt(initialProviders[i], 1);
                    if (position > -1) {
                        registeredProviderNames.add(initialProviders[i].getName());
                    }
                }
                return null;
            });
        }

        if (finalProviders != null) {
            SecurityActions.doPrivileged((PrivilegedAction<Void>) () -> {
                for (Provider current : finalProviders) {
                    int position = Security.addProvider(current);
                    if (position > -1) {
                        registeredProviderNames.add(current.getName());
                    }
                }
                return null;
            });
        }
    }

    @Override
    public void stop(StopContext context) {
        Iterator<String> namesIterator = registeredProviderNames.iterator();
        SecurityActions.doPrivileged((PrivilegedAction<Void>) () -> {
            while (namesIterator.hasNext()) {
                Security.removeProvider(namesIterator.next());
                namesIterator.remove();
            }
            return null;
        });
    }

    Injector<Provider[]> getInitialProivders() {
        return initialProviders;
    }

    Injector<Provider[]> getFinalProviders() {
        return finalProviders;
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

}
