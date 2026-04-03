/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.auditlog;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogLoggerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.stability.StabilityServerSetupSnapshotRestoreTasks;

@RunWith(WildFlyRunner.class)
@ServerSetup({StabilityServerSetupSnapshotRestoreTasks.Community.class})
public class AuditLogRedactedTestCase {

    @Inject
    public ManagementClient managementClient;

    public Path logDir = new File(System.getProperty("jboss.home")).toPath()
            .resolve("standalone")
            .resolve("data")
            .resolve("audit-log.log")
            .toAbsolutePath();

    public PathAddress auditLogConfigAddress = PathAddress.pathAddress(
            CoreManagementResourceDefinition.PATH_ELEMENT,
            AccessAuditResourceDefinition.PATH_ELEMENT,
            AuditLogLoggerResourceDefinition.PATH_ELEMENT);

    private static final PathAddress CREDENTIAL_STORE_ADDRESS = PathAddress.pathAddress(
            PathElement.pathElement("subsystem", "elytron"),
            PathElement.pathElement("credential-store", "test-audit-cs"));

    @Before
    public void before() throws Exception {
        Files.deleteIfExists(logDir);

        ModelNode op = Util.getWriteAttributeOperation(
                auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.ENABLED.getName(),
                ModelNode.TRUE);

        managementClient.executeForResult(op);

        op = Util.getWriteAttributeOperation(
                auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.LOG_READ_ONLY.getName(),
                ModelNode.TRUE);

        managementClient.executeForResult(op);

        Assert.assertTrue(Files.exists(logDir));
    }

    @After
    public void after() throws Exception {
        removeCredentialStore();
        setAuditLogRedacted(true);

        ModelNode op = Util.getWriteAttributeOperation(
                auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.LOG_READ_ONLY.getName(),
                ModelNode.FALSE);
        managementClient.executeForResult(op);

        op = Util.getWriteAttributeOperation(
                auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.ENABLED.getName(),
                ModelNode.FALSE);

        managementClient.executeForResult(op);
        Assert.assertTrue(Files.exists(logDir));

        Files.delete(logDir);
    }

    @Test
    public void testEnableAndDisableCoreAuditLog() throws Exception {
        String storePassword = "storeSecretPassword";
        String alias = "audit-alias";
        String secondAlias = "audit-alias-2";
        String secretValue = "auditSecretValue";
        String expectedHash = sha256(secretValue);

        createCredentialStore(storePassword);

        try {
            Files.delete(logDir);

            ModelNode addAlias = Util.createOperation("add-alias", CREDENTIAL_STORE_ADDRESS);
            addAlias.get("alias").set(alias);
            addAlias.get("secret-value").set(secretValue);
            managementClient.executeForResult(addAlias);

            List<ModelNode> records = readFile(logDir.toFile(), 1);
            List<ModelNode> ops = records.get(0).get("ops").asList();
            Assert.assertEquals(1, ops.size());
            ModelNode op = ops.get(0);
            Assert.assertEquals("add-alias", op.get(ModelDescriptionConstants.OP).asString());
            Assert.assertEquals(expectedHash, op.get("secret-value").asString());
            Assert.assertNotEquals(secretValue, op.get("secret-value").asString());

            ModelNode removeAlias = Util.createOperation("remove-alias", CREDENTIAL_STORE_ADDRESS);
            removeAlias.get("alias").set(alias);
            managementClient.executeForResult(removeAlias);

            setAuditLogRedacted(false);
            Files.deleteIfExists(logDir);

            addAlias = Util.createOperation("add-alias", CREDENTIAL_STORE_ADDRESS);
            addAlias.get("alias").set(secondAlias);
            addAlias.get("secret-value").set(secretValue);
            managementClient.executeForResult(addAlias);

            records = readFile(logDir.toFile(), 1);
            ops = records.get(0).get("ops").asList();
            Assert.assertEquals(1, ops.size());
            op = ops.get(0);
            Assert.assertEquals("add-alias", op.get(ModelDescriptionConstants.OP).asString());
            Assert.assertEquals(secretValue, op.get("secret-value").asString());

            removeAlias = Util.createOperation("remove-alias", CREDENTIAL_STORE_ADDRESS);
            removeAlias.get("alias").set(secondAlias);
            managementClient.executeForResult(removeAlias);
        } finally {
            setAuditLogRedacted(true);
            removeCredentialStore();
        }
    }

    private void createCredentialStore(String storePassword) throws Exception {
        ModelNode addOp = Util.createAddOperation(CREDENTIAL_STORE_ADDRESS);
        addOp.get("relative-to").set("jboss.server.data.dir");
        addOp.get("location").set("test-audit.store");
        addOp.get("create").set(true);
        ModelNode credRef = new ModelNode();
        credRef.get("clear-text").set(storePassword);
        addOp.get("credential-reference").set(credRef);
        managementClient.executeForResult(addOp);
    }

    private void removeCredentialStore() throws Exception {
        try {
            ModelNode remove = Util.createRemoveOperation(CREDENTIAL_STORE_ADDRESS);
            managementClient.executeForResult(remove);

            // Clean up the file created by the credential store
            Path storePath = new File(System.getProperty("jboss.home")).toPath()
                    .resolve("standalone")
                    .resolve("data")
                    .resolve("test-audit.store")
                    .toAbsolutePath();
            Files.deleteIfExists(storePath);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private void setAuditLogRedacted(boolean redacted) throws Exception {
        ModelNode op = Util.getWriteAttributeOperation(
                auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.REDACTED.getName(),
                redacted ? ModelNode.TRUE : ModelNode.FALSE);
        managementClient.executeForResult(op);
    }


    private final Pattern DATE_STAMP_PATTERN = Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d - \\{");

    private List<ModelNode> readFile(File file, int expectedRecords) throws IOException {
        List<ModelNode> list = new ArrayList<>();

        try (final BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            StringWriter writer = null;
            String line = reader.readLine();
            while (line != null) {
                if (DATE_STAMP_PATTERN.matcher(line).matches()) {
                    if (writer != null) {
                        list.add(ModelNode.fromJSONString(writer.getBuffer().toString()));
                    }
                    writer = new StringWriter();
                    writer.append("{");
                } else {
                    Assert.assertNotNull(writer);
                    writer.append("\n");
                    writer.append(line);
                }
                line = reader.readLine();
            }
            if (writer != null) {
                list.add(ModelNode.fromJSONString(writer.getBuffer().toString()));
            }
        }
        Assert.assertEquals(list.toString(), expectedRecords, list.size());
        return list;
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
