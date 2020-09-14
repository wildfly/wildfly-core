/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.domain.management.security;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.jboss.as.controller.services.path.PathEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManager.Callback;
import org.jboss.as.controller.services.path.PathManager.Event;
import org.jboss.as.controller.services.path.PathManager.PathEventContext;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * An extension to {@link AbstractTrustManagerService} so that a TrustManager[] can be provided based on a JKS file based key
 * store.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class FileTrustManagerService extends AbstractTrustManagerService {

    private final Supplier<PathManager> pathManagerSupplier;

    private volatile String provider;
    private volatile String path;
    private volatile String relativeTo;

    private volatile TrustManagerFactory trustManagerFactory;
    private volatile FileKeystore keyStore;

    FileTrustManagerService(final Consumer<TrustManager[]> trustManagersConsumer,
                            final Supplier<PathManager> pathManagerSupplier,
                            final ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier,
                            final String provider, final String path, final String relativeTo, final char[] keystorePassword) {
        super(trustManagersConsumer, credentialSourceSupplier, keystorePassword);
        this.pathManagerSupplier = pathManagerSupplier;
        this.provider = provider;
        this.path = path;
        this.relativeTo = relativeTo;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRelativeTo() {
        return relativeTo;
    }

    public void setRelativeTo(final String relativeTo) {
        this.relativeTo = relativeTo;
    }

    /*
     * Service Lifecycle Methods
     */

    @Override
    public void start(StartContext context) throws StartException {
        try {
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw DomainManagementLogger.ROOT_LOGGER.unableToStart(e);
        }
        // Do our initialisation first as the parent implementation will
        // expect that to be complete.
        String file = path;
        if (relativeTo != null) {
            PathManager pm = pathManagerSupplier.get();

            file = pm.resolveRelativePathEntry(file, relativeTo);
            pm.registerCallback(relativeTo, new Callback() {

                @Override
                public void pathModelEvent(PathEventContext eventContext, String name) {
                    if (eventContext.isResourceServiceRestartAllowed() == false) {
                        eventContext.reloadRequired();
                    }
                }

                @Override
                public void pathEvent(Event event, PathEntry pathEntry) {
                    // Service dependencies should trigger a stop and start.
                }
            }, Event.REMOVED, Event.UPDATED);
        }
        keyStore = FileKeystore.newTrustStore(provider, file, resolvePassword());
        keyStore.load();

        super.start(context);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        keyStore = null;
    }

    @Override
    protected TrustManager[] createTrustManagers() throws NoSuchAlgorithmException, KeyStoreException {
        KeyStore trustStore = loadTrustStore();
        trustManagerFactory.init(trustStore);

        TrustManager[] tmpTrustManagers = trustManagerFactory.getTrustManagers();
        TrustManager[] trustManagers = new TrustManager[tmpTrustManagers.length];
        boolean disableDynamic = isDisableDynamicTrustManager();
        for (int i = 0; i < tmpTrustManagers.length; i++) {
            trustManagers[i] = disableDynamic
                    ? tmpTrustManagers[i]
                    : new DelegatingTrustManager((X509TrustManager) tmpTrustManagers[i], keyStore);
        }

        return trustManagers;
    }

    @Override
    protected KeyStore loadTrustStore() {
        return keyStore.getKeyStore();
    }

    private boolean isDisableDynamicTrustManager() {
        String prop = WildFlySecurityManager.getPropertyPrivileged("jboss.as.management.security.disable-dynamic-trust-manager", "false");
        return "true".equalsIgnoreCase(prop);
    }

    private class DelegatingTrustManager implements X509TrustManager {

        private X509TrustManager delegate;
        private final FileKeystore theTrustStore;

        private DelegatingTrustManager(X509TrustManager trustManager, FileKeystore theTrustStore) {
            this.delegate = trustManager;
            this.theTrustStore = theTrustStore;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            getDelegate().checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            getDelegate().checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return getDelegate().getAcceptedIssuers();
        }

        /*
         * Internal Methods
         */
        private synchronized X509TrustManager getDelegate() {
            if (theTrustStore.isModified()) {
                try {
                    theTrustStore.load();
                } catch (StartException e1) {
                    throw DomainManagementLogger.ROOT_LOGGER.unableToLoadKeyTrustFile(e1.getCause());
                }
                try {
                    trustManagerFactory.init(theTrustStore.getKeyStore());
                    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                    for (TrustManager current : trustManagers) {
                        if (current instanceof X509TrustManager) {
                            delegate = (X509TrustManager) current;
                            break;
                        }
                    }
                } catch (GeneralSecurityException e) {
                    throw DomainManagementLogger.ROOT_LOGGER.unableToOperateOnTrustStore(e);

                }
            }
            if (delegate == null) {
                throw DomainManagementLogger.ROOT_LOGGER.unableToCreateDelegateTrustManager();
            }

            return delegate;
        }

    }

}
