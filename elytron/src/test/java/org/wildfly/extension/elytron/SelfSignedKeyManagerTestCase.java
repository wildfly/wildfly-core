/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.keystore.AtomicLoadKeyStore;
import org.wildfly.security.keystore.UnmodifiableKeyStore;
import org.wildfly.security.password.interfaces.ClearPassword;

/** Covers self-signed generation before the key-store MSC service exists. */
public class SelfSignedKeyManagerTestCase {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void firstUseBeforeServiceStartGeneratesAndPersistsInCanonicalStore() throws Exception {
        char[] storePassword = "storePass123".toCharArray();
        char[] keyPassword = "keyPass123".toCharArray();
        File file = new File(temporaryFolder.getRoot(), "generated.jks");
        AtomicLoadKeyStore atomic = AtomicLoadKeyStore.newInstance("JKS");
        atomic.load(null, storePassword);
        KeyStore exported = UnmodifiableKeyStore.unmodifiableKeyStore(atomic);
        CredentialSource source = IdentityCredentials.NONE.withCredential(new PasswordCredential(
                ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, storePassword)));

        KeyStoreDefinition.KeyStoreDoohickey doohickey = new KeyStoreDefinition.KeyStoreDoohickey(
                PathAddress.pathAddress(PathElement.pathElement("key-store", "early")));
        DoohickeySimultaneity.withLockForLifecycle(() -> {
            doohickey.cacheEarlyValue(atomic, file, source, exported);
            return null;
        });
        SSLDefinitions.LazyDelegatingKeyManager manager = new SSLDefinitions.LazyDelegatingKeyManager(
                () -> null, doohickey, keyPassword,
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()), "localhost", null);

        assertFalse(file.exists());
        X509Certificate[] chain = manager.getCertificateChain("server");
        assertNotNull(chain);
        assertEquals(1, chain.length);
        assertTrue(file.exists());
        assertSame(atomic, doohickey.getCachedAtomicKeyStore());
        assertSame(exported, doohickey.getForService(() -> {
            fail("Service startup must reuse the early key-store value");
            return null;
        }));

        // Simulate a relative-to base changing between early access and MSC startup by supplying
        // a different service path. The service must retain the first initializer's file.
        File laterResolvedFile = new File(temporaryFolder.getRoot(), "later.jks");
        KeyStoreService service = KeyStoreService.createFileBasedKeyStoreService(
                null, "JKS", null, laterResolvedFile.getAbsolutePath(), false, null);
        service.setDoohickey(doohickey);
        service.start(null);
        assertSame(exported, service.getValue());
        assertEquals(file.getAbsolutePath(), service.getResolvedAbsolutePath());
        assertFalse(laterResolvedFile.exists());

        KeyStore persisted = KeyStore.getInstance("JKS");
        try (FileInputStream input = new FileInputStream(file)) {
            persisted.load(input, storePassword);
        }
        assertTrue(persisted.containsAlias("server"));
        assertEquals(chain[0], persisted.getCertificate("server"));
        assertNotNull(persisted.getKey("server", keyPassword));
        service.stop(null);
    }
}
