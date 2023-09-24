/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */


package org.jboss.as.controller.security;

import static org.jboss.as.controller.security.CredentialReference.updateCredentialStore;

import org.jboss.dmr.ModelNode;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;


/**
 * A {@link Service} responsible for automatic updates of {@link CredentialStore}s.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */

public class CredentialStoreUpdateService implements Service<CredentialStoreUpdateService> {

    private String alias;
    private String secret;
    private ModelNode result;
    private CredentialStoreUpdateInfo credentialStoreUpdateInfo;

    private final InjectedValue<CredentialStore> injectedCredentialStore = new InjectedValue<>();

    CredentialStoreUpdateService(String alias, String secret, ModelNode result) {
        this.alias = alias;
        this.secret = secret;
        this.result = result;
        this.credentialStoreUpdateInfo = null;
    }

    CredentialStoreUpdateService(String alias, String secret, ModelNode result, CredentialStoreUpdateInfo credentialStoreUpdateInfo) {
        this.alias = alias;
        this.secret = secret;
        this.result = result;
        this.credentialStoreUpdateInfo = credentialStoreUpdateInfo;
    }

    /*
     * Service Lifecycle Related Methods
     */

    @Override
    public void start(StartContext startContext) throws StartException {
        if (alias != null && secret != null) {
            try {
                updateCredentialStore(injectedCredentialStore.getValue(), alias, secret, result, credentialStoreUpdateInfo);
            } catch (CredentialStoreException e) {
                throw new StartException(e);
            }
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        this.alias = null;
        this.secret = null;
        this.result = null;
    }

    @Override
    public synchronized CredentialStoreUpdateService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    Injector<CredentialStore> getCredentialStoreInjector() {
        return injectedCredentialStore;
    }

    public static ServiceName createServiceName(String parentName, String credentialStoreName) {
        return ServiceName.of("org", "wildfly", "security", "elytron").append("credential-store-update", parentName + "-" + credentialStoreName);
    }

}
