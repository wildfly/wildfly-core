/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.credential.SecretKeyCredential;

/**
 * Test case testing operations against a credential store using the management operations.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class CredentialStoreTestCase extends AbstractSubsystemTest {

    private static final Provider PROVIDER = new WildFlyElytronProvider();

    private static final String CONFIGURATION = "credential-store.xml";

    private KernelServices services = null;

    public CredentialStoreTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Before
    public void init() throws Exception {
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource(CONFIGURATION).build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }
    }

    @BeforeClass
    public static void initTests() throws Exception {
        AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            public Integer run() {
                return Security.insertProviderAt(PROVIDER, 1);
            }
        });
    }

    @AfterClass
    public static void cleanUpTests() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                Security.removeProvider(PROVIDER.getName());

                return null;
            }
        });
    }

    @Test
    public void testSecretKeyFlowDefault() {
        testSecretKeyGenerateExportImport(-1);
    }

    @Test
    public void testSecretKeyFlow128() {
        testSecretKeyGenerateExportImport(128);
    }

    @Test
    public void testSecretKeyFlow192() {
        testSecretKeyGenerateExportImport(192);
    }

    @Test
    public void testSecretKeyFlow256() {
        testSecretKeyGenerateExportImport(256);
    }

    private void testSecretKeyGenerateExportImport(final int keySize) {
        String alias = keySize > 0 ? "test" + keySize : "testDefault";

        // Generate and store a new SecretKey using the specified keySize.
        ModelNode generate = new ModelNode();
        generate.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        generate.get(ClientConstants.OP).set("generate-secret-key");
        generate.get(ElytronDescriptionConstants.ALIAS).set(alias);
        if (keySize > 0) {
            generate.get(ElytronDescriptionConstants.KEY_SIZE).set(keySize);
        }
        assertSuccess(services.executeOperation(generate));

        // Export the generated SecretKey
        ModelNode export = new ModelNode();
        export.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        export.get(ClientConstants.OP).set("export-secret-key");
        export.get(ElytronDescriptionConstants.ALIAS).set(alias);
        ModelNode exportResult = assertSuccess(services.executeOperation(export));

        final String key = exportResult.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();

        String importAlias = alias + "Import";
        // Import the previously exported SecretKey
        ModelNode importKey = new ModelNode();
        importKey.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        importKey.get(ClientConstants.OP).set("import-secret-key");
        importKey.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        importKey.get(ElytronDescriptionConstants.KEY).set(key);
        assertSuccess(services.executeOperation(importKey));

        // Re-export so keys can be compared
        ModelNode export2 = new ModelNode();
        export2.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        export2.get(ClientConstants.OP).set("export-secret-key");
        export2.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        ModelNode exportResult2 = assertSuccess(services.executeOperation(export2));

        final String key2 = exportResult2.get(ClientConstants.RESULT).get(ElytronDescriptionConstants.KEY).asString();
        assertNotNull("Exported SecretKey", key);
        assertEquals("Matching keys", key, key2);

        // Remove aliases
        ModelNode remove = new ModelNode();
        remove.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        remove.get(ClientConstants.OP).set("remove-alias");
        remove.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        remove.get(ElytronDescriptionConstants.ALIAS).set(alias);
        assertSuccess(services.executeOperation(remove));

        ModelNode remove2 = new ModelNode();
        remove2.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add("credential-store", "test");
        remove2.get(ClientConstants.OP).set("remove-alias");
        remove2.get(ElytronDescriptionConstants.ENTRY_TYPE).set(SecretKeyCredential.class.getSimpleName());
        remove2.get(ElytronDescriptionConstants.ALIAS).set(importAlias);
        assertSuccess(services.executeOperation(remove2));
    }

    private ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

}
