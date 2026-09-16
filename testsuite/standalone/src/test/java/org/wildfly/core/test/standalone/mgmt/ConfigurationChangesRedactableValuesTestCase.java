/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REDACTED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.stability.StabilityServerSetupSnapshotRestoreTasks;

@RunWith(WildFlyRunner.class)
@ServerSetup({StabilityServerSetupSnapshotRestoreTasks.Community.class})
public class ConfigurationChangesRedactableValuesTestCase {

    private static final int MAX_HISTORY_SIZE = 8;

    private static final PathAddress CONFIGURATION_CHANGES_ADDRESS = PathAddress.pathAddress()
            .append(PathElement.pathElement(SUBSYSTEM, "core-management"))
            .append(PathElement.pathElement("service", "configuration-changes"));

    private static final PathAddress CREDENTIAL_STORE_ADDRESS = PathAddress.pathAddress()
            .append(PathElement.pathElement(SUBSYSTEM, "elytron"))
            .append(PathElement.pathElement("credential-store", "test-cs"));

    @Inject
    protected ManagementClient client;


    @Before
    public void createConfigurationChanges() throws Exception {
        final ModelNode add = Util.createAddOperation(CONFIGURATION_CHANGES_ADDRESS);
        add.get("max-history").set(MAX_HISTORY_SIZE);
        client.executeForResult(add);
    }

    @After
    public void clearConfigurationChanges() throws UnsuccessfulOperationException {
        final ModelNode remove = Util.createRemoveOperation(CONFIGURATION_CHANGES_ADDRESS);
        client.executeForResult(remove);
    }

    @Test
    public void testRedactableAttribute() throws Exception {
        String storePassword = "storeSecretPassword";
        String expectedHash = sha256(storePassword);

        ModelNode addOp = Util.createAddOperation(CREDENTIAL_STORE_ADDRESS);
        addOp.get("relative-to").set("jboss.server.data.dir");
        addOp.get("location").set("test-redact.store");
        addOp.get("create").set(true);
        ModelNode credRef = new ModelNode();
        credRef.get("clear-text").set(storePassword);
        addOp.get("credential-reference").set(credRef);
        client.executeForResult(addOp);

        try {
            List<ModelNode> changes = getConfigurationChanges();
            assertFalse("Expected at least one configuration change", changes.isEmpty());

            // The latest change
            ModelNode latestChange = changes.get(0);
            assertEquals(SUCCESS, latestChange.get(OUTCOME).asString());
            ModelNode recordedOp = latestChange.get(OPERATIONS).asList().get(0);
            assertEquals(ADD, recordedOp.get(OP).asString());

            assertTrue("credential-reference should be present in the recorded operation", recordedOp.hasDefined("credential-reference"));
            ModelNode recordedCredRef = recordedOp.get("credential-reference");

            assertTrue("clear-text should be present inside credential-reference", recordedCredRef.hasDefined("clear-text"));
            String recordedClearText = recordedCredRef.get("clear-text").asString();
            assertNotEquals("clear-text should not contain the original value", storePassword, recordedClearText);
            assertEquals("clear-text should be replaced with SHA-256 hash of the original value", expectedHash, recordedClearText);
        } finally {
            client.executeForResult(Util.createRemoveOperation(CREDENTIAL_STORE_ADDRESS));
        }

        ServerReload.executeReloadAndWaitForCompletion(client.getControllerClient());

        // repeat the test but now with redaction disabled
        ModelNode writeOp = Util.getWriteAttributeOperation(CONFIGURATION_CHANGES_ADDRESS, REDACTED, false);
        client.executeForResult(writeOp);

        addOp = Util.createAddOperation(CREDENTIAL_STORE_ADDRESS);
        addOp.get("relative-to").set("jboss.server.data.dir");
        addOp.get("location").set("test-redact.store");
        credRef = new ModelNode();
        credRef.get("clear-text").set(storePassword);
        addOp.get("credential-reference").set(credRef);
        client.executeForResult(addOp);

        try {
            List<ModelNode> changes = getConfigurationChanges();
            assertFalse("Expected at least one configuration change", changes.isEmpty());

            // The latest change
            ModelNode latestChange = changes.get(0);
            assertEquals(SUCCESS, latestChange.get(OUTCOME).asString());
            ModelNode recordedOp = latestChange.get(OPERATIONS).asList().get(0);
            assertEquals(ADD, recordedOp.get(OP).asString());

            assertTrue("credential-reference should be present in the recorded operation", recordedOp.hasDefined("credential-reference"));
            ModelNode recordedCredRef = recordedOp.get("credential-reference");

            assertTrue("clear-text should be present inside credential-reference", recordedCredRef.hasDefined("clear-text"));
            String recordedClearText = recordedCredRef.get("clear-text").asString();
            assertEquals("clear-text should contain the original value", storePassword, recordedClearText);
        } finally {
            client.executeForResult(Util.createRemoveOperation(CREDENTIAL_STORE_ADDRESS));
        }
    }

    private List<ModelNode> getConfigurationChanges() throws UnsuccessfulOperationException {
        ModelNode listChanges = Util.createOperation("list-changes", CONFIGURATION_CHANGES_ADDRESS);
        ModelNode result = client.executeForResult(listChanges);
        return result.asList();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
