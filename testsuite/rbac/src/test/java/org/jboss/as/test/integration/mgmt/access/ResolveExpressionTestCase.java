/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerSetup(StandardUsersSetupTask.class)
public class ResolveExpressionTestCase extends AbstractRbacTestCase {

    private static final ModelNode SYSTEM_PROP_READ = createReadNode("system-property");
    private static final ModelNode JVM_READ = createReadNode( "jvm");

    private static ModelNode createReadNode(String classification) {
        final String address = "core-service=management/access=authorization/" +
                "constraint=sensitivity-classification/type=core/classification=" + classification;
        ModelNode result = createOpNode(address, WRITE_ATTRIBUTE_OPERATION);
        result.get(NAME).set("configured-requires-read");
        result.get(VALUE); // leave it undefined
        result.protect();
        return result;
    }

    @After
    public void restoreDefaultConfig() throws UnsuccessfulOperationException {
        // Go back to default settings by undefining the configured-requires-read settings
        try {
            managementClient.executeForResult(SYSTEM_PROP_READ);
        } finally {
            managementClient.executeForResult(JVM_READ);
        }
    }

    @Test
    public void testDefaultSettings() throws IOException {
        testUserSet(false);
    }

    /** Tests that resolution works if sys-prop reads are configured to be allowed. */
    @Test
    public void testNonSensitiveSystemPropertyRead() throws IOException, UnsuccessfulOperationException {
        ModelNode allowRead = SYSTEM_PROP_READ.clone();
        allowRead.get(VALUE).set(false);
        managementClient.executeForResult(allowRead);

        testUserSet(true);
    }

    /** Tests that resolution fails even though sys-prop reads are allowed if jvm reads are not */
    @Test
    public void testSensitiveJvmRead() throws IOException, UnsuccessfulOperationException {
        ModelNode allowRead = SYSTEM_PROP_READ.clone();
        allowRead.get(VALUE).set(false);
        managementClient.executeForResult(allowRead);

        ModelNode disallowRead = JVM_READ.clone();
        disallowRead.get(VALUE).set(true);
        managementClient.executeForResult(disallowRead);

        testUserSet(false);
    }

    private void testUserSet(boolean allCanRead) throws IOException {
        testUser(RbacUtil.MONITOR_USER, allCanRead);
        testUser(RbacUtil.OPERATOR_USER, allCanRead);
        testUser(RbacUtil.MAINTAINER_USER, allCanRead);
        testUser(RbacUtil.DEPLOYER_USER, allCanRead);
        testUser(RbacUtil.ADMINISTRATOR_USER, true);
        testUser(RbacUtil.AUDITOR_USER, true);
        testUser(RbacUtil.SUPERUSER_USER, true);
    }

    private void testUser(String userName, boolean canRead) throws IOException {
        ModelControllerClient client = getClientForUser(userName);

        ModelNode operation = createOpNode(null, "resolve-expression");
        operation.get("expression").set("${foo:bar}");
        ModelNode response = client.execute(operation);
        if (canRead) {
            assertEquals(response.toString(), SUCCESS, response.get(OUTCOME).asString());
            assertEquals(response.toString(), "bar", response.get(RESULT).asString());
        } else {
            assertEquals(response.toString(), FAILED, response.get(OUTCOME).asString());
            assertFalse(response.toString(), response.hasDefined(RESULT));
            assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0313"));
        }
    }
}
