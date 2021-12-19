/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.wildfly.test.integration.elytron.expression;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.encryption.SecretKeyUtil;
import org.wildfly.security.password.interfaces.ClearPassword;

@RunWith(WildflyTestRunner.class)
@ServerSetup(SystemPropertyExpressionTestCase.ServerSetup.class)
public class SystemPropertyExpressionTestCase {

    private static final String CNAME = SystemPropertyExpressionTestCase.class.getSimpleName();
    private static final String CS_PATH = "target/" + CNAME + ".cs";
    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(SUBSYSTEM, "elytron");
    private static final PathAddress ENCRYPTION_ADDRESS = SUBSYSTEM_ADDRESS.append("expression", "encryption");
    private static final String CREDENTIAL_STORE = "credential-store";
    private static final PathAddress CREDENTIAL_STORE_ADDRESS = SUBSYSTEM_ADDRESS.append(CREDENTIAL_STORE, CNAME);
    private static final String SECURE_KEY = "RUxZAUsXHVcDh99zAdxGEzTBK1h2qjW+sZg2+37w7ijhDEiJEw==";
    private static final PathAddress RUNTIME_ADDRESS = PathAddress.pathAddress(CORE_SERVICE, PLATFORM_MBEAN).append(TYPE, "runtime");
    private static final String PROP = CNAME;
    private static final String MISSING_PROP = PROP + "-missing";

    private static final String CLEAR_TEXT = "Lorem ipsum dolor sit amet";

    public static final class ServerSetup implements ServerSetupTask {
        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            addCredentialStore(managementClient);
            managementClient.executeForResult(getAddExpressionEncyryptionOp());
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            try {
                safeRemoveSystemProperty(managementClient, PROP);
                safeRemoveSystemProperty(managementClient, MISSING_PROP);
                removeExpressionEncryption(managementClient.getControllerClient());
            } finally {
                removeCredentialStore(managementClient);
            }
        }

        private static void  addCredentialStore(ManagementClient managementClient) throws GeneralSecurityException, IOException, UnsuccessfulOperationException {
            cleanCredentialStoreFile();
            KeyStore ks = KeyStore.getInstance("JCEKS");
            ks.load(null, null);
            ks.store(new FileOutputStream(CS_PATH), CNAME.toCharArray());

            Map<String, String> attributes = new HashMap<>();
            attributes.put("location", CS_PATH);
            attributes.put("keyStoreType", "JCEKS");
            attributes.put("modifiable", "true");

            PasswordCredential credential = new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, CNAME.toCharArray()));
            CredentialStore credentialStore = CredentialStore.getInstance(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE);

            credentialStore.initialize(attributes,
                    new CredentialStore.CredentialSourceProtectionParameter(IdentityCredentials.NONE.withCredential(credential)));
            credentialStore.store("securekey", new SecretKeyCredential(SecretKeyUtil.importSecretKey(SECURE_KEY)));
            credentialStore.flush();

            ModelNode addOp = Util.createAddOperation(CREDENTIAL_STORE_ADDRESS);
            addOp.get("location").set(CS_PATH);
            addOp.get("credential-reference", "clear-text").set(CNAME);
            managementClient.executeForResult(addOp);
        }

        private static void safeRemoveSystemProperty(ManagementClient managementClient, String prop) {
            try {
                managementClient.getControllerClient().execute(Util.createRemoveOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, prop)));
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }

        private static void removeCredentialStore(ManagementClient managementClient) throws UnsuccessfulOperationException {
            try {
                ModelNode removeOp = Util.createRemoveOperation(CREDENTIAL_STORE_ADDRESS);
                managementClient.executeForResult(removeOp);
                ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
            } finally {
                cleanCredentialStoreFile();
            }
        }

        private static void cleanCredentialStoreFile() {
            File f = new File(CS_PATH);
            assert !f.exists() || f.delete();
        }
    }

    private static ModelNode getAddExpressionEncyryptionOp() {
        ModelNode encAdd = Util.createAddOperation(ENCRYPTION_ADDRESS);
        encAdd.get("default-resolver").set("Default");
        ModelNode resolvers = encAdd.get("resolvers");
        ModelNode resolver = new ModelNode();
        resolver.get("name").set("Default");
        resolver.get(CREDENTIAL_STORE).set(CNAME);
        resolver.get("secret-key").set("securekey");
        resolvers.add(resolver);
        return  encAdd;
    }

    private static void removeExpressionEncryption(ModelControllerClient modelControllerClient) throws IOException {
        ModelNode removeOp = Util.createRemoveOperation(ENCRYPTION_ADDRESS);
        removeOp.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        assertSuccess(modelControllerClient.execute(removeOp));
    }

    @Test
    public void testEncryptedSystemProperties() throws Exception {

        PathAddress propAddress = PathAddress.pathAddress(SYSTEM_PROPERTY, PROP);
        try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {

            assertNull(getSystemProperty(client, PROP));
            assertNull(getSystemProperty(client, MISSING_PROP));

            ModelNode response = createExpression(client);
            assertSuccess(response);
            String expression = response.get(ClientConstants.RESULT).get("expression").asString();

            ModelNode addOp = Util.createAddOperation(propAddress);
            addOp.get(VALUE).set(expression);

            response = client.execute(addOp);
            assertSuccess(response);

            assertEquals(CLEAR_TEXT, getSystemProperty(client, PROP));

            removeExpressionEncryption(client);

            // Removing the resolver doesn't affect the system property
            assertEquals(CLEAR_TEXT, getSystemProperty(client, PROP));

            ModelNode missingAddOp = Util.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, MISSING_PROP));
            missingAddOp.get(VALUE).set(expression);
            // The add should succeed, but the expression would be resolved as if it were a standard expression with a default
            assertSuccess(client.execute(missingAddOp));
            assertEquals(expression.substring(expression.indexOf(":Default:"), expression.length() - 1), getSystemProperty(client, MISSING_PROP));

            // Clean up
            assertSuccess(client.execute(Util.createRemoveOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, MISSING_PROP))));
            assertNull(getSystemProperty(client, MISSING_PROP));

            // If the prop and the resolver are added in a composite, then proper resolution can occur during op execution
            ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
            ModelNode steps = composite.get(STEPS);
            steps.add(missingAddOp);

            steps.add(getAddExpressionEncyryptionOp());

            assertSuccess(client.execute(composite));

            assertEquals(CLEAR_TEXT, getSystemProperty(client, MISSING_PROP));

            // Test boot behavior. Reload and confirm the property is set.
            ServerReload.executeReloadAndWaitForCompletion(client);

            assertEquals(CLEAR_TEXT, getSystemProperty(client, PROP));

        }
    }

    private static ModelNode createExpression(ModelControllerClient client) throws IOException {
        ModelNode createExpression = Util.createEmptyOperation("create-expression", ENCRYPTION_ADDRESS);
        createExpression.get("resolver").set("Default");
        createExpression.get("clear-text").set(CLEAR_TEXT);

        return client.execute(createExpression);
    }

    private static void assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
    }

    private static String getSystemProperty(ModelControllerClient client, String property) throws IOException {
        ModelNode response = client.execute(Util.getReadAttributeOperation(RUNTIME_ADDRESS, "system-properties"));
        assertSuccess(response);
        ModelNode val = response.get(RESULT, property);
        return val.isDefined() ? val.asString() : null;
    }
}
