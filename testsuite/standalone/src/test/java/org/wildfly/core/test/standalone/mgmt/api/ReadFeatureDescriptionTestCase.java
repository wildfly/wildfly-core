/*
Copyright 2018 Red Hat, Inc.

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

package org.wildfly.core.test.standalone.mgmt.api;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANNOTATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FEATURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_FEATURE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE_DEPTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test of read-feature-description handling.
 *
 * @author Brian Stansberry
 */
@RunWith(WildflyTestRunner.class)
public class ReadFeatureDescriptionTestCase {

    @SuppressWarnings("unused")
    @Inject
    private static ManagementClient managementClient;

    @Test
    public void testRootReadFeature() throws UnsuccessfulOperationException {
        ModelNode op = createOp();
        ModelNode result = executeForResult(op);
        validateBaseFeature(result);
    }

    @Test
    public void testNonRootReadFeature() throws UnsuccessfulOperationException {
        ModelNode op = createOp(PathAddress.pathAddress(EXTENSION, "logging"));
        ModelNode result = executeForResult(op);
        validateBaseFeature(result);
    }

    @Test
    public void testWildcardReadFeature() throws UnsuccessfulOperationException {
        ModelNode op = createOp(PathAddress.pathAddress(SOCKET_BINDING_GROUP, "*"));
        ModelNode result = executeForResult(op);
        Assert.assertEquals(result.toString(), ModelType.LIST, result.getType());
        for (ModelNode element : result.asList()) {
            Assert.assertTrue(element.toString(), element.hasDefined(OP_ADDR));
            Assert.assertEquals(element.toString(), SUCCESS, element.get(OUTCOME).asString());
            Assert.assertTrue(element.toString(), element.hasDefined(RESULT));
            validateBaseFeature(element.get(RESULT));
        }
    }

    @Test
    public void testRecursiveReadFeature() throws UnsuccessfulOperationException {
        ModelNode op = createOp();
        op.get(RECURSIVE).set(true);
        ModelNode result = executeForResult(op);
        int maxDepth = validateBaseFeature(result, Integer.MAX_VALUE);
        Assert.assertTrue(result.toString(), maxDepth > 3); // >3 is a good sign we're recursing all the way
    }

    @Test
    public void testRecursiveReadFeatureMaxDepth() throws UnsuccessfulOperationException {
        ModelNode op = createOp();
        op.get(RECURSIVE_DEPTH).set(1);
        ModelNode result = executeForResult(op);
        int maxDepth = validateBaseFeature(result, 1);
        Assert.assertEquals(result.toString(), 1, maxDepth);
    }

    private static ModelNode createOp() {
        return createOp(PathAddress.EMPTY_ADDRESS);
    }

    private static ModelNode createOp(PathAddress address) {
        return Util.createEmptyOperation(READ_FEATURE_DESCRIPTION_OPERATION, address);
    }

    private ModelNode executeForResult(ModelNode op) throws UnsuccessfulOperationException {
        ModelNode result = managementClient.executeForResult(op);
        Assert.assertTrue(result.isDefined());
        return result;
    }

    private static void validateBaseFeature(ModelNode base) {
        validateBaseFeature(base, 0);
    }

    private static int validateBaseFeature(ModelNode base, int maxChildDepth) {
        Assert.assertTrue(base.toString(), base.hasDefined(FEATURE));
        Assert.assertEquals(base.toString(), 1, base.asInt());
        return validateFeature(base.get(FEATURE), null, maxChildDepth, 0);
    }

    private static int validateFeature(ModelNode feature, String expectedName, int maxChildDepth, int featureDepth) {
        int highestDepth = featureDepth;
        for (Property prop : feature.asPropertyList()) {
            switch (prop.getName()) {
                case NAME:
                    if (expectedName != null) {
                        Assert.assertEquals(feature.toString(), expectedName, prop.getValue().asString());
                    }
                    break;
                case CHILDREN:
                    if (prop.getValue().isDefined()) {
                        Assert.assertTrue(feature.toString(), maxChildDepth > 0);
                        for (Property child : prop.getValue().asPropertyList()) {
                            int treeDepth = validateFeature(child.getValue(), child.getName(),
                                    maxChildDepth - 1, featureDepth + 1);
                            highestDepth = Math.max(highestDepth, treeDepth);
                        }
                    }
                    break;
                case ANNOTATION:
                case "params":
                case "refs":
                case "provides":
                case "requires":
                case "packages":
                    // all ok; no other validation right now
                    break;
                default:
                    Assert.fail("Unknown key " + prop.getName() + " in " + feature.toString());
            }
        }
        return highestDepth;
    }
}
