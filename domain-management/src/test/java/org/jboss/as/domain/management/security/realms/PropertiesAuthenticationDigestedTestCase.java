/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.security.realms;

import static org.junit.Assert.assertArrayEquals;
import static org.wildfly.security.password.interfaces.DigestPassword.ALGORITHM_DIGEST_MD5;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.sasl.RealmCallback;

import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.AuthorizingCallbackHandler;
import org.jboss.as.domain.management.security.operations.SecurityRealmAddBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.callback.EvidenceVerifyCallback;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;

/**
 * A test case to test authentication against a properties file.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PropertiesAuthenticationDigestedTestCase extends SecurityRealmTestBase {

    private static final String TEST_USERNAME = "TestUser";
    private static final String TEST_PASSWORD = "TestPassword";

    protected File propertiesFile;

    @BeforeClass
    public static void installProvider() {
        Security.insertProviderAt(new WildFlyElytronProvider(), 0);
    }

    @Test
    public void testSupportedMechanism() {
        Set<AuthMechanism> supportedMechs = securityRealm.getSupportedAuthenticationMechanisms();
        assertEquals("Number of mechanims", 1, supportedMechs.size());
        assertTrue("Supports Digest", supportedMechs.contains(AuthMechanism.DIGEST));
    }

    @Test
    public void testObtainDigest() throws Exception {
        AuthorizingCallbackHandler cbh = securityRealm.getAuthorizingCallbackHandler(AuthMechanism.DIGEST);

        NameCallback ncb = new NameCallback("Username", TEST_USERNAME);
        RealmCallback rcb = new RealmCallback("Realm", TEST_REALM);
        CredentialCallback cc = new CredentialCallback(PasswordCredential.class, ALGORITHM_DIGEST_MD5);

        cbh.handle(new Callback[] { ncb, rcb, cc });

        UsernamePasswordHashUtil uph = new UsernamePasswordHashUtil();
        byte[] expected = uph.generateHashedURP(TEST_USERNAME, TEST_REALM, TEST_PASSWORD.toCharArray());

        assertArrayEquals("Expected hash", expected, ((DigestPassword) ((PasswordCredential) cc.getCredential()).getPassword()).getDigest());
    }

    @Test
    public void testVerifyGoodPassword() throws Exception {
        AuthorizingCallbackHandler cbh = securityRealm.getAuthorizingCallbackHandler(AuthMechanism.DIGEST);

        NameCallback ncb = new NameCallback("Username", TEST_USERNAME);
        RealmCallback rcb = new RealmCallback("Realm", TEST_REALM);
        EvidenceVerifyCallback evc = new EvidenceVerifyCallback(new PasswordGuessEvidence(TEST_PASSWORD.toCharArray()));

        cbh.handle(new Callback[] { ncb, rcb, evc });

        assertTrue("Password Verified", evc.isVerified());
    }

    @Test
    public void testVerifyBadPassword() throws Exception {
        AuthorizingCallbackHandler cbh = securityRealm.getAuthorizingCallbackHandler(AuthMechanism.DIGEST);

        NameCallback ncb = new NameCallback("Username", TEST_USERNAME);
        RealmCallback rcb = new RealmCallback("Realm", TEST_REALM);
        EvidenceVerifyCallback evc = new EvidenceVerifyCallback(new PasswordGuessEvidence("BAD PASSWORD".toCharArray()));

        cbh.handle(new Callback[] { ncb, rcb, evc });

        assertFalse("Password Not Verified", evc.isVerified());
    }

    @Test
    public void testBadUser() throws Exception {
        AuthorizingCallbackHandler cbh = securityRealm.getAuthorizingCallbackHandler(AuthMechanism.DIGEST);

        NameCallback ncb = new NameCallback("Username", "BadUser");
        RealmCallback rcb = new RealmCallback("Realm", TEST_REALM);
        CredentialCallback cc = new CredentialCallback(PasswordCredential.class, ALGORITHM_DIGEST_MD5);

        try {
            cbh.handle(new Callback[] { ncb, rcb, cc });
            fail("Expected exception not thrown.");
        } catch (IOException e) {
        }
    }

    @Override
    protected void initialiseRealm(SecurityRealmAddBuilder builder) throws Exception {
        if (propertiesFile == null) {
            propertiesFile = new File(".");
            propertiesFile = new File(propertiesFile, "target");
            propertiesFile = new File(propertiesFile, "properties").getCanonicalFile();
            if (!propertiesFile.exists()) {
                propertiesFile.mkdirs();
            }

            propertiesFile = new File(propertiesFile, "test-users.properties").getCanonicalFile();
        }

        UsernamePasswordHashUtil uph = new UsernamePasswordHashUtil();
        Files.write(propertiesFile.toPath(), (TEST_USERNAME + "=" + uph.generateHashedHexURP(TEST_USERNAME, TEST_REALM, TEST_PASSWORD.toCharArray())).getBytes(StandardCharsets.UTF_8));

        builder.authentication().property().setPath(propertiesFile.getAbsolutePath()).build().build();
    }

}
