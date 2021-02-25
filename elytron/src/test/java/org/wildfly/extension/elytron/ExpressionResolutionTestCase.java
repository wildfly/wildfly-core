/*
 * Copyright 2021 Red Hat, Inc.
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.credential.store.WildFlyElytronCredentialStoreProvider;

/**
 * A test case to test expression resolution using the expression=encryption resource.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ExpressionResolutionTestCase extends AbstractSubsystemBaseTest {

    private static final Provider PROVIDER = new WildFlyElytronCredentialStoreProvider();

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

    public ExpressionResolutionTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("expression-encryption.xml");
    }

    @Test
    public void testPreConfiguredHierarchy() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("expression-encryption.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }

        testExpectedAliases(services, "secret-key-credential-store", "CredentialStoreThree", "testkey", "key");
        testExpectedAliases(services, "credential-store", "CredentialStoreTwo", "csone");
        testExpectedAliases(services, "credential-store", "CredentialStoreOne", "ksone", "securekey");
        // No aliases in the key stores but we want them to be up to be queried.
        testExpectedAliases(services, "key-store", "KeyStoreOne");
        testExpectedAliases(services, "key-store", "KeyStoreTwo");
    }

    private void testExpectedAliases(KernelServices services, String resourceType, String resourceName,
            String... expectedAliases) throws Exception {

        ModelNode readAliases = new ModelNode();
        readAliases.get(ClientConstants.OP_ADDR).add("subsystem", "elytron").add(resourceType, resourceName);
        readAliases.get(ClientConstants.OP).set(ElytronDescriptionConstants.READ_ALIASES);

        ModelNode readAliasesResult = assertSuccess(services.executeOperation(readAliases));

        ModelNode result = readAliasesResult.get(ClientConstants.RESULT);
        if (expectedAliases.length > 0) {
            Set<String> expectedSet = new HashSet<>(Arrays.asList(expectedAliases));

            List<ModelNode> aliasList = result.asList();
            for (ModelNode aliasNode : aliasList) {
                String alias = aliasNode.asString();
                if (!expectedSet.remove(alias)) {
                    fail("Alias '" + alias + "' not expected.");
                }
            }

            assertEquals("All expected aliases found", 0, expectedSet.size());
        } else {
            assertTrue("Undefined result expected.", !result.isDefined());
        }
    }

    private static ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

}
