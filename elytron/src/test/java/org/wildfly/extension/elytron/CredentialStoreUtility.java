/*
 * JBoss, Home of Professional Open Source
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.jboss.logging.Logger;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.credential.store.impl.PropertiesCredentialStore;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * CredentialStoreUtility is a utility class that can handle dynamic CredentialStore creation, manipulation and deletion/removal of keystore file.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 *
 */
public class CredentialStoreUtility {

    private static Logger LOGGER = Logger.getLogger(CredentialStoreUtility.class);

    private final String credentialStoreFileName;
    private final CredentialStore credentialStore;
    static final String DEFAULT_PASSWORD = "super_secret";

    /**
     * Create Credential Store.
     *
     * @param credentialStoreFileName name of file to hold credentials
     * @param storePassword master password (clear text) to open the credential store
     * @param adminKeyPassword a password (clear text) for protecting admin key
     * @param createStorageFirst flag whether to create storage first and then initialize Credential Store
     */
    public CredentialStoreUtility(String credentialStoreFileName, String storePassword, String adminKeyPassword, boolean createStorageFirst, boolean propertiesStore) {
        this.credentialStoreFileName = Assert.checkNotNullParam("credentialStoreFileName", credentialStoreFileName);
        if (!propertiesStore) {
            Assert.checkNotNullParam("storePassword", storePassword);
            Assert.checkNotNullParam("adminKeyPassword", adminKeyPassword);
        }

        try {
            Map<String, String> attributes = new HashMap<>();
            if (propertiesStore) {
                credentialStore = CredentialStore.getInstance(PropertiesCredentialStore.NAME);
                attributes.put("location", credentialStoreFileName);
                attributes.put("create", "true");
                credentialStore.initialize(attributes);
            } else {
                if (createStorageFirst) {
                    createKeyStore("JCEKS", storePassword.toCharArray());
                }
                credentialStore = CredentialStore.getInstance(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE);
                attributes.put("location", credentialStoreFileName);
                attributes.put("keyStoreType", "JCEKS");
                attributes.put("modifiable", "true");
                if (!createStorageFirst) {
                    File storage = new File(credentialStoreFileName);
                    if (storage.exists()) {
                        storage.delete();
                    }
                }

                credentialStore.initialize(attributes, new CredentialStore.CredentialSourceProtectionParameter(
                        IdentityCredentials.NONE.withCredential(convertToPasswordCredential(storePassword.toCharArray()))));
            }
        } catch (Throwable t) {
            LOGGER.error(t);
            throw new RuntimeException(t);
        }
        LOGGER.debugf("Credential Store created [%s] with password \"%s\"", credentialStoreFileName, storePassword);
    }

    /**
     * Create Credential Store.
     * Automatically crate underlying KeyStore.
     *
     * @param credentialStoreFileName name of file to hold credentials
     * @param storePassword master password (clear text) to open the credential store
     */
    public CredentialStoreUtility(String credentialStoreFileName, String storePassword) {
        this(credentialStoreFileName, storePassword, storePassword, true, false);
    }

    /**
     * Create Credential Store with default password.
     * Automatically create underlying KeyStore.
     *
     * @param credentialStoreFileName name of file to hold credentials
     */
    public CredentialStoreUtility(String credentialStoreFileName) {
        this(credentialStoreFileName, DEFAULT_PASSWORD);
    }

    /**
     * Create Credential Store with default password.
     * Automatically create underlying KeyStore.
     *
     * @param credentialStoreFileName name of file to hold credentials
     */
    public CredentialStoreUtility(String credentialStoreFileName, boolean propertiesCredentialStore) {
        this(credentialStoreFileName, DEFAULT_PASSWORD, DEFAULT_PASSWORD, true, propertiesCredentialStore);
    }

    /**
     * Add new entry to credential store and perform all conversions.
     * @param alias of the entry
     * @param clearTextPassword password
     */
    public void addEntry(String alias, String clearTextPassword) {
        try {
            credentialStore.store(alias, new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, clearTextPassword.toCharArray())));
            credentialStore.flush();
        } catch (Exception e) {
            LOGGER.error(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Add new entry to credential store and perform all conversions.
     * @param alias of the entry
     * @param clearTextPassword password
     */
    public void addEntry(String alias, SecretKey secretKey) {
        try {
            credentialStore.store(alias, new SecretKeyCredential(secretKey));
            credentialStore.flush();
        } catch (Exception e) {
            LOGGER.error(e);
            throw new RuntimeException(e);
        }
    }

    private void createKeyStore(String keyStoreType, char[] keyStorePwd) throws Exception {
        KeyStore ks = KeyStore.getInstance(keyStoreType);
        ks.load((InputStream)null, null);
        ks.store(new FileOutputStream(new File(credentialStoreFileName)), keyStorePwd);
    }

    /**
     * Delete associated files.
     */
    public void cleanUp() {
        deleteIfExists(new File(credentialStoreFileName));
    }

    private static void deleteIfExists(File f) {
        assert !f.exists() || f.delete();
    }

    /**
     * Convert {@code char[]} password to {@code PasswordCredential}
     * @param password to convert
     * @return new {@code PasswordCredential}
     */
    public static PasswordCredential convertToPasswordCredential(char[] password) {
        return new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, password));
    }

}
