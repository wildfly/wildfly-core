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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * Service to handle the creation of the KeyManager[].
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
abstract class AbstractKeyManagerService implements Service {

    private volatile char[] keystorePassword;
    private volatile char[] keyPassword;
    private final Consumer<AbstractKeyManagerService> keyManagerServiceConsumer;
    private final ExceptionSupplier<CredentialSource, Exception> keyCredentialSourceSupplier;
    private final ExceptionSupplier<CredentialSource, Exception> keystoreCredentialSourceSupplier;


    AbstractKeyManagerService(final Consumer<AbstractKeyManagerService> keyManagerServiceConsumer,
                              final ExceptionSupplier<CredentialSource, Exception> keyCredentialSourceSupplier,
                              final ExceptionSupplier<CredentialSource, Exception> keystoreCredentialSourceSupplier,
                              final char[] keystorePassword, final char[] keyPassword) {
        this.keyManagerServiceConsumer = keyManagerServiceConsumer;
        this.keyCredentialSourceSupplier = keyCredentialSourceSupplier;
        this.keystoreCredentialSourceSupplier = keystoreCredentialSourceSupplier;
        this.keystorePassword = keystorePassword;
        this.keyPassword = keyPassword;
    }

    public char[] getKeystorePassword() {
        return keystorePassword;
    }


    public void setKeystorePassword(char[] keystorePassword) {
        this.keystorePassword = keystorePassword;
    }


    public char[] getKeyPassword() {
        return keyPassword;
    }


    public void setKeyPassword(char[] keyPassword) {
        this.keyPassword = keyPassword;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        try {
            createKeyManagers(true);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            throw DomainManagementLogger.ROOT_LOGGER.unableToStart(e);
        }
        keyManagerServiceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        keyManagerServiceConsumer.accept(null);
    }

    public KeyManager[] getKeyManagers() {
        try {
            return createKeyManagers(false);
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract boolean isLazy();

    /**
     * Method to create the KeyManager[]
     *
     * This method returns the created KeyManager[] so that sub classes can have the opportunity to either wrap or replace this
     * call.
     *
     * @param startup If true the keymanagers are being created on startup. If they key manager creation is lazy then it is ok to return null
     * @return The KeyManager[] based on the supplied {@link KeyStore}
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws UnrecoverableKeyException
     */
    protected KeyManager[] createKeyManagers(boolean startup) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
        KeyStore keyStore = loadKeyStore(startup);
        if(keyStore == null && startup) {
            return null;
        }

        char[] keyPass = resolveKeyPassword();
        if (keyPass == null) keyPass = resolveKeystorePassword();

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyPass);

        return keyManagerFactory.getKeyManagers();
    }

    protected abstract KeyStore loadKeyStore(boolean startup);

    static final class ServiceUtil {

        private static final String SERVICE_SUFFIX = "key-manager";

        public static ServiceName createServiceName(final ServiceName parentService) {
            return parentService.append(SERVICE_SUFFIX);
        }

        public static Supplier<AbstractKeyManagerService> requires(final ServiceBuilder<?> sb, final ServiceName parentService) {
            return sb.requires(createServiceName(parentService));
        }

    }

    protected char[] resolveKeyPassword() {
        return resolvePassword(keyCredentialSourceSupplier, keyPassword);
    }

    protected char[] resolveKeystorePassword() {
        return resolvePassword(keystoreCredentialSourceSupplier, keystorePassword);
    }

    private char[] resolvePassword(ExceptionSupplier<CredentialSource, Exception> sourceSupplier, char[] legacyPassword) {
        try {
            if(sourceSupplier == null) {
                return legacyPassword;
            }
            CredentialSource cs = sourceSupplier.get();
            if(cs == null) {
                return legacyPassword;
            }
            org.wildfly.security.credential.PasswordCredential credential = cs.getCredential(org.wildfly.security.credential.PasswordCredential.class);
            if (credential == null) {
                return legacyPassword;
            }
            ClearPassword password = credential.getPassword(ClearPassword.class);
            if (password == null) {
                return legacyPassword;
            }
            return password.getPassword();
        } catch (Exception ex) {
            return legacyPassword;
        }
    }
}
