/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt.api.core;

import java.io.IOException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(WildFlyRunner.class)
public class PatchInfoUnitTestCase extends ContainerResourceMgmtTestBase {

    private static final PathAddress ROOT_RESOURCE = PathAddress.pathAddress(PathElement.pathElement(CORE_SERVICE, "patching"));
    private static final PathAddress BASE_LAYER = ROOT_RESOURCE.append(PathElement.pathElement("layer", "base"));

    /**
     * Skip this testcase if patching is not enabled.
     */
    @Before
    public void assumePatchingIsEnabled() throws IOException, MgmtOperationException {
        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(ROOT_RESOURCE.toModelNode());
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        final ModelNode result = executeOperation(operation, false);
        Assume.assumeTrue(result.get(ModelDescriptionConstants.OUTCOME).asString()
                .equals(ModelDescriptionConstants.SUCCESS));
    }

    @Test
    public void testRootInfo() throws Exception {

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(ROOT_RESOURCE.toModelNode());
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(RECURSIVE).set(false);
        operation.get(INCLUDE_RUNTIME).set(true);

        final ModelNode result = executeOperation(operation, true);
        Assert.assertTrue(result.hasDefined("cumulative-patch-id"));
        Assert.assertTrue(result.hasDefined("patches"));

    }

    @Test
    public void testBaseLayer() throws Exception {

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(BASE_LAYER.toModelNode());
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(RECURSIVE).set(false);
        operation.get(INCLUDE_RUNTIME).set(true);

        final ModelNode result = executeOperation(operation, true);
        Assert.assertTrue(result.hasDefined("cumulative-patch-id"));
        Assert.assertTrue(result.hasDefined("patches"));

    }


    @Test
    public void testDescription() throws Exception {

        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(ROOT_RESOURCE.toModelNode());
        operation.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
        operation.get(RECURSIVE).set(true);
        operation.get(INCLUDE_RUNTIME).set(true);

        executeOperation(operation, true);
    }

}
