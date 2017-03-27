/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.server.services.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.VaultReader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Unit tests of {@link RuntimeVaultReader}.
 *
 * @author Brian Stansberry
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RuntimeVaultReaderUnitTestCase {

    public static final String VAULTED_DATA = "VAULT::test::test::12345";
    public static final String SECRET = "secret";

    private static VaultHandler vaultHandler;

    @BeforeClass
    public static void beforeClass() {
        vaultHandler = new VaultHandler();
    }

    @AfterClass
    public static void afterClass() {
        vaultHandler.cleanUp();
    }

    @Test
    public void testANotInitialized() {
        RuntimeVaultReader testee = new RuntimeVaultReader();
        checkVaultedData(testee);
        testFailedLookup(testee);
    }

    private static void checkVaultedData(RuntimeVaultReader testee) {
        assertTrue(testee.isVaultFormat(VAULTED_DATA));
    }

    private  void testFailedLookup(RuntimeVaultReader testee) {
        try {
            String data = testee.retrieveFromVault(VAULTED_DATA);
            fail("Should have failed with unitialized vault, instead got " + data);
        } catch (VaultReader.NoSuchItemException expected) {
            // good
        } catch (Exception e) {
            fail("Wrong exception type " + e);
        }
    }

    @Test
    public void testBFailedCreate() {
        RuntimeVaultReader testee = new RuntimeVaultReader();
        // This class isn't a valid value for the class attribute
        try {
            testee.createVault(getClass().getCanonicalName(), null, Collections.singletonMap("a", "b"));
            fail("createVault did not fail");
        } catch (VaultReaderException good) {
            // pass
        }
        checkVaultedData(testee);
        testFailedLookup(testee);
    }

    @Test
    public void testCLookupMiss() {
        RuntimeVaultReader testee = new RuntimeVaultReader();
        createVault(testee, vaultHandler);
        checkVaultedData(testee);
        testFailedLookup(testee);
    }

    @Test
    public void testDLookupSuccess() throws Exception {

        storeSecret(vaultHandler);

        RuntimeVaultReader testee = new RuntimeVaultReader();
        createVault(testee, vaultHandler);
        checkVaultedData(testee);
        String result = testee.retrieveFromVault(VAULTED_DATA);
        assertEquals(SECRET, result);
    }

    public static void createVault(RuntimeVaultReader testee, VaultHandler vaultHandler) {
        Map<String, Object> options = getOptions(vaultHandler);
        testee.createVault(null, null, options);
    }

    private static Map<String, Object> getOptions(VaultHandler vaultHandler) {
        Map<String, Object> vaultOptions = new HashMap<>();
        vaultOptions.put("KEYSTORE_URL", vaultHandler.getKeyStore());
        vaultOptions.put("KEYSTORE_PASSWORD", vaultHandler.getMaskedKeyStorePassword());
        vaultOptions.put("KEYSTORE_ALIAS", vaultHandler.getAlias());
        vaultOptions.put("SALT", vaultHandler.getSalt());
        vaultOptions.put("ITERATION_COUNT", vaultHandler.getIterationCountAsString());
        vaultOptions.put("ENC_FILE_DIR", vaultHandler.getEncodedVaultFileDirectory());
        return vaultOptions ;
    }

    public static void storeSecret(VaultHandler vaultHandler) throws Exception {

        VaultSession session = vaultHandler.getVaultSession();
        char[] secret = SECRET.toCharArray();
        session.addSecuredAttribute("test", "test", secret);

    }
}
