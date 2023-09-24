/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt.api;

import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.DeploymentScannerSetupTask;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;


/**
 * Tests that the deployment scanner can be added and removed using management APIs
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 * @author Jaikiran Pai - Updated to fix intermittent failures as reported in https://issues.jboss.org/browse/WFLY-1554
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({DeploymentScannerSetupTask.class})
public class DeploymentScannerTestCase extends ContainerResourceMgmtTestBase {

    private static final Logger logger = Logger.getLogger(DeploymentScannerTestCase.class);

    private static final String tempDir = System.getProperty("java.io.tmpdir");
    private static File deployDir;

    @Before
    public void before() throws IOException, MgmtOperationException {
        deployDir = new File(tempDir + File.separator + "tempDeployment");
        if (deployDir.exists()) {
            FileUtils.deleteDirectory(deployDir);
        }
        assertTrue("Unable to create deployment scanner directory.", deployDir.mkdir());
    }

    @After
    public void after() throws IOException, MgmtOperationException {
        FileUtils.deleteDirectory(deployDir);
    }

    /**
     * 1) Executes an add operation for a test deployment scanner. This is expected to pass
     * 2) Tests that the test deployment scanner resource added in step#1, exists
     * 3) Finally, removes the test deployment scanner
     *
     */
    @Test
    public void testAddRemove() throws Exception {

        addDeploymentScanner();
        Throwable caught = null;
        try {
            assertTestDeploymentScannerResourceExists();
        }  catch (Exception | Error t) {
            caught = t;
        } finally {
            // now clean up
            try {
                removeDeploymentScanner();
            } catch (Throwable t) {
                if (caught != null) {
                    // just log so that the any previous exception that might have failed this test will be propagated back
                    logger.info("Removing test deployment scanner failed with exception", t);
                } else {
                    caught = t;
                }
            }
        }
        if (caught instanceof Exception) {
            throw (Exception) caught;
        } else if (caught != null) {
            throw (Error) caught;
        }
    }

    /**
     * Adds a deployment scanner (let's say "foo") which points to a non-existing deployment directory. This operation is expected to fail and the test asserts it.
     * Later it adds a deployment scanner with the same name, but this time points to a correct/existing deployment directory. This operation is then expected to pass.
     *
     */
    @Test
    public void testAddWrongPath() throws Exception {

        // add deployment scanner with non existing path
        final ModelNode addScannerForNonExistentPath = createOpNode("subsystem=deployment-scanner/scanner=testScanner", "add");
        addScannerForNonExistentPath.get("scan-interval").set(1000);
        addScannerForNonExistentPath.get("path").set("/tmp/DeploymentScannerTestCase/nonExistingPath");

        final ModelNode result = executeOperation(addScannerForNonExistentPath, false);
        // check that it failed
        assertEquals("Adding a deployment scanner for a non-existent path was expected to succeed but it failed", "success", result.get("outcome").asString());

        Throwable caught = null;
        try {
            assertTestDeploymentScannerResourceExists();
        } catch (Exception | Error t) {
            caught = t;
        } finally {
            // now clean up
            try {
                removeDeploymentScanner();
            } catch (Throwable t) {
                if (caught != null) {
                    // just log so that the any previous exception that might have failed this test will be propagated back
                    logger.info("Removing test deployment scanner failed with exception", t);
                } else {
                    caught = t;
                }
            }
        }
        if (caught instanceof Exception) {
            throw (Exception) caught;
        } else if (caught != null) {
            throw (Error) caught;
        }
    }

    /**
     * 1) Triggers a composite operation, a step of which includes the add operation for the test deployment scanner.
     * 2) #1 step is supposed to fail and the test deployment scanner is *not* expected to be present after that failure
     * 3) After #2, executes an add operation which adds a test deployment scanner and that's supposed to pass
     * 4) The presence of the deployment scanner added in #3 is asserted
     * 5) Another composite operation is triggered and that's expected to rollback/fail
     * 6) After #5 step the presence of the test deployment scanner added in #3 step is asserted. It should still exist
     *
     */
    @Test
    public void testAddRemoveRollbacks() throws Exception {

        // execute and rollback add deployment scanner
        final ModelNode addOp = getAddDeploymentScannerOp();
        final ModelNode resultOfRollback = executeAndRollbackOperation(addOp);
        assertEquals("Unexpected result from invoking an operation which was supposed to fail", "failed", resultOfRollback.get("outcome").asString());

        // add deployment scanner - this time it's supposed to be added successfully
        addDeploymentScanner();
        Throwable caught = null;
        try {
            assertTestDeploymentScannerResourceExists();
            // execute and rollback remove deployment scanner
            final ModelNode removeOp = getRemoveDeploymentScannerOp();
            final ModelNode resultOfRemoveRollback = executeAndRollbackOperation(removeOp);
            assertEquals("Unexpected result from invoking an operation which was supposed to fail", "failed", resultOfRemoveRollback.get("outcome").asString());

            // check that the deployment scanner is still present
            assertTestDeploymentScannerResourceExists();
        } catch (Exception | Error t) {
            caught = t;
        } finally {
            // now clean up
            try {
                removeDeploymentScanner();
            } catch (Throwable t) {
                if (caught != null) {
                    // just log so that the any previous exception that might have failed this test will be propagated back
                    logger.info("Removing test deployment scanner failed with exception", t);
                } else {
                    caught = t;
                }
            }
        }
        if (caught instanceof Exception) {
            throw (Exception) caught;
        } else if (caught != null) {
            throw (Error) caught;
        }
    }

    private void addDeploymentScanner() throws Exception {
        // add deployment scanner
        final ModelNode op = getAddDeploymentScannerOp();
        final ModelNode resp = executeOperation(op, false);
        assertEquals("Unexpected outcome " + resp + " of adding the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS, resp.get("outcome").asString());
    }

    private void assertTestDeploymentScannerResourceExists() throws Exception {
        final ModelNode readResourceOp = Util.createOperation(ModelDescriptionConstants.READ_RESOURCE_OPERATION, getTestDeploymentScannerResourcePath());
        final ModelNode result = executeOperation(readResourceOp, false);
        assertEquals(readResourceOp + " was supposed to be successful but received " + result, ModelDescriptionConstants.SUCCESS, result.get("outcome").asString());
    }

    private void removeDeploymentScanner() throws Exception {
        // remove deployment scanner
        final ModelNode op = getRemoveDeploymentScannerOp();
        final ModelNode resp = executeOperation(op, false);
        assertEquals("Unexpected outcome " + resp + " of removing the test deployment scanner: " + op, ModelDescriptionConstants.SUCCESS, resp.get("outcome").asString());
    }

    private ModelNode getAddDeploymentScannerOp() {
        final ModelNode op = Util.createAddOperation(getTestDeploymentScannerResourcePath());
        op.get("scan-interval").set(1000);
        op.get("path").set(deployDir.getAbsolutePath());
        return op;
    }

    private ModelNode getRemoveDeploymentScannerOp() {
        return createOpNode("subsystem=deployment-scanner/scanner=testScanner", "remove");
    }

    private PathAddress getTestDeploymentScannerResourcePath() {
        return PathAddress.pathAddress(PathElement.pathElement("subsystem", "deployment-scanner"), PathElement.pathElement("scanner", "testScanner"));
    }
}
