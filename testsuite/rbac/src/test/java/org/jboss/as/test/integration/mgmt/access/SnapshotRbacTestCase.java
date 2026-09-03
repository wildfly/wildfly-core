/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LIST_SNAPSHOTS_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TAKE_SNAPSHOT_OPERATION;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.ADMINISTRATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.AUDITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.DEPLOYER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MAINTAINER_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.MONITOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.OPERATOR_USER;
import static org.jboss.as.test.integration.management.rbac.RbacUtil.SUPERUSER_USER;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests that the {@code take-snapshot} operation sanitizes the user-supplied {@code name} parameter to prevent path traversal.
 * <p>
 * All standard RBAC roles can invoke {@code take-snapshot}, so the test verifies that for every role the snapshot file is
 * always written inside the snapshots directory, even when the name contains directory traversal sequences.
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({ StandardUsersSetupTask.class })
public class SnapshotRbacTestCase extends AbstractRbacTestCase {

    @After
    public void cleanupSnapshots() throws Exception {
        ModelNode op = createOpNode(null, "delete-snapshot");
        op.get(NAME).set("all");
        getManagementClient().getControllerClient().execute(op);
    }

    @Test
    public void testMonitor() throws Exception {
        testSnapshotTraversal(MONITOR_USER);
    }

    @Test
    public void testOperator() throws Exception {
        testSnapshotTraversal(OPERATOR_USER);
    }

    @Test
    public void testMaintainer() throws Exception {
        testSnapshotTraversal(MAINTAINER_USER);
    }

    @Test
    public void testDeployer() throws Exception {
        testSnapshotTraversal(DEPLOYER_USER);
    }

    @Test
    public void testAdministrator() throws Exception {
        testSnapshotTraversal(ADMINISTRATOR_USER);
    }

    @Test
    public void testAuditor() throws Exception {
        testSnapshotTraversal(AUDITOR_USER);
    }

    @Test
    public void testSuperUser() throws Exception {
        testSnapshotTraversal(SUPERUSER_USER);
    }

    private void testSnapshotTraversal(String userName) throws Exception {
        ModelControllerClient client = getClientForUser(userName);
        Path snapshotsDirPath = Paths.get(getSnapshotsDirectory(client)).normalize();

        ModelNode takeOp = Operations.createOperation(TAKE_SNAPSHOT_OPERATION);
        takeOp.get(NAME).set("rbac-test-");
        ModelNode response = RbacUtil.executeOperation(client, takeOp, Outcome.SUCCESS);
        Path normalFilePath = Paths.get(response.get(RESULT).asString()).normalize();
        assertTrue("Normal snapshot path should be within snapshots directory: " + normalFilePath,
                normalFilePath.startsWith(snapshotsDirPath));

        assertSnapshotContained(client, snapshotsDirPath, "../../../../traversal-");

        if (File.separatorChar == '\\') {
            assertSnapshotContained(client, snapshotsDirPath, "..\\..\\..\\..\\traversal-");
        }
    }

    private void assertSnapshotContained(ModelControllerClient client, Path snapshotsDirPath, String maliciousName)
            throws Exception {
        ModelNode takeOp = Operations.createOperation(TAKE_SNAPSHOT_OPERATION);
        takeOp.get(NAME).set(maliciousName);
        ModelNode response = RbacUtil.executeOperation(client, takeOp, Outcome.SUCCESS);
        Path traversalFilePath = Paths.get(response.get(RESULT).asString()).normalize();

        assertTrue("Traversal snapshot path must be within snapshots directory: " + traversalFilePath,
                traversalFilePath.startsWith(snapshotsDirPath));
    }

    private String getSnapshotsDirectory(ModelControllerClient client) throws Exception {
        ModelNode listOp = Operations.createOperation(LIST_SNAPSHOTS_OPERATION);
        ModelNode response = client.execute(listOp);
        assertTrue(response.toString(), Outcome.SUCCESS.toString().equalsIgnoreCase(response.get(OUTCOME).asString()));
        return response.get(RESULT).get(DIRECTORY).asString();
    }
}
