/*
Copyright 2019 Red Hat, Inc.

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
package org.wildfly.core.test.standalone.mgmt;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.optypes.OpTypesExtension;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

import javax.inject.Inject;
import java.io.IOException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests adding and removing extensions and subsystems in a composite op.
 *
 * @author Brian Stansberry
 */
@RunWith(WildFlyRunner.class)
public class ExtensionSubsystemCompositeTestCase {

    private static final PathAddress EXT = PathAddress.pathAddress("extension", OpTypesExtension.EXTENSION_NAME);
    private static final PathAddress SUBSYSTEM = PathAddress.pathAddress("subsystem", OpTypesExtension.SUBSYSTEM_NAME);

    @Inject
    private static ManagementClient managementClient;

    @Before
    public void installExtensionModule() throws IOException {
        // We use OpTypesExtension for this test because it's convenient. Doesn't do anything crazy
        // and exposes an op ('public') that we can call to check the subsystem is added and functions
        ExtensionUtils.createExtensionModule(OpTypesExtension.EXTENSION_NAME, OpTypesExtension.class,
                EmptySubsystemParser.class.getPackage());
    }

    @After
    public void removeExtensionModule() {

        try {
            executeOp(Util.createRemoveOperation(SUBSYSTEM), SUCCESS);
        } catch (Throwable ignored) {
            // assume subsystem wasn't there
        } finally {
            try {
                executeOp(Util.createRemoveOperation(EXT), SUCCESS);
            } catch (Throwable t) {
                // assume extension wasn't there
            } finally {
                ExtensionUtils.deleteExtensionModule(OpTypesExtension.EXTENSION_NAME);
            }
        }
    }

    @Test
    public void test() throws IOException {

        // 1) Sanity check -- subsystem not there
        ModelNode invokePublic = Util.createEmptyOperation("public", SUBSYSTEM);
        testBadOp(invokePublic);

        // 2) sanity check -- subsystem add w/o extension -- fail
        ModelNode subAdd = Util.createAddOperation(SUBSYSTEM);
        testBadOp(subAdd);

        // 3) ext add + sub add + sub other in composite
        ModelNode extAdd = Util.createAddOperation(EXT);
        ModelNode goodAdd = buildComposite(extAdd, subAdd, invokePublic);
        testGoodComposite(goodAdd);

        // 4) Sanity check -- try invokePublic again outside the composite
        ModelNode response = executeOp(invokePublic, "success");
        assertTrue(response.toString(), response.has("result"));
        assertTrue(response.toString(), response.get("result").asBoolean());

        // 5) sub remove + ext remove + sub add in composite -- fail
        ModelNode subRemove = Util.createRemoveOperation(SUBSYSTEM);
        ModelNode extRemove = Util.createRemoveOperation(EXT);
        ModelNode badRemove = buildComposite(invokePublic, subRemove, extRemove, subAdd);
        response = testBadOp(badRemove);
        // But the 'public' op should have worked
        validateInvokePublicStep(response, 1, true);

        // 6) sub remove + ext remove in composite
        ModelNode goodRemove = buildComposite(invokePublic, subRemove, extRemove);
        response = executeOp(goodRemove, "success");
        validateInvokePublicStep(response, 1, false);

        // 7) confirm ext add + sub add + sub other still works
        testGoodComposite(goodAdd);

        // 8) Sanity check -- try invokePublic again outside the composite
        response = executeOp(invokePublic, "success");
        assertTrue(response.toString(), response.has("result"));
        assertTrue(response.toString(), response.get("result").asBoolean());
    }

    private ModelNode executeOp(ModelNode op, String outcome) throws IOException {
        ModelNode response = managementClient.getControllerClient().execute(op);
        assertTrue(response.toString(), response.hasDefined(OUTCOME));
        assertEquals(response.toString(), outcome, response.get(OUTCOME).asString());
        return response;
    }

    private void testGoodComposite(ModelNode composite) throws IOException {
        ModelNode result = executeOp(composite, "success");
        validateInvokePublicStep(result, 3, false);
    }

    private ModelNode testBadOp(ModelNode badOp) throws IOException {
        ModelNode response = executeOp(badOp, "failed");
        String msg = response.toString();
        assertTrue(msg, response.has("failure-description"));
        ModelNode failure = response.get("failure-description");
        assertTrue(msg, failure.asString().contains("WFLYCTL0030"));
        return response;
    }

    private static ModelNode buildComposite(ModelNode... steps) {
        ModelNode result = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        ModelNode stepsParam = result.get("steps");
        for (ModelNode step : steps) {
            stepsParam.add(step);
        }
        return result;
    }

    private static void validateInvokePublicStep(ModelNode response, int step, boolean expectRollback) {
        String msg = response.toString();
        assertTrue(msg, response.has("result"));
        ModelNode result = response.get("result");
        assertTrue(msg, result.isDefined());
        String stepKey = "step-"+step;
        assertEquals(msg, expectRollback ? "failed" : "success", result.get(stepKey, "outcome").asString());
        assertTrue(msg, result.has(stepKey, "result"));
        assertTrue(msg, result.get(stepKey, "result").asBoolean());
        if (expectRollback) {
            assertTrue(msg, result.has(stepKey, "rolled-back"));
            assertTrue(msg, result.get(stepKey, "rolled-back").asBoolean());
        } else {
            assertFalse(msg, result.has(stepKey, "rolled-back"));
        }
    }

}
