/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt.api.core;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE_LOADING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;

import java.io.File;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests of the core-service=module-loading resource.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
@RunWith(WildFlyRunner.class)
public class ModuleLoadingManagementTestCase extends ContainerResourceMgmtTestBase {
    private static final PathAddress RESOURCE = PathAddress.pathAddress(PathElement.pathElement(CORE_SERVICE, MODULE_LOADING));

    private static final String MODULES_DIR = File.separator + "modules";
    private static final String LAYERS_BASE = MODULES_DIR + File.separator + "system" + File.separator
            + "layers" + File.separator + "base";

    @Test
    public void testModuleRootsAttribute() throws Exception {

        ModelNode op = Util.createEmptyOperation("read-attribute", RESOURCE);
        op.get(NAME).set("module-roots");

        ModelNode response = getModelControllerClient().execute(op);
        List<ModelNode> result = validateResponse(response).asList();
        boolean hasModules = false;
        boolean hasBase = false;
        for (ModelNode node : result) {
            String root = node.asString();
            if (root.endsWith(MODULES_DIR)) {
                hasModules = true;
            }
            if (root.endsWith(LAYERS_BASE)) {
                Assert.assertFalse(hasBase);
                hasBase = true;
            }
        }
        Assert.assertTrue(hasModules);
        Assert.assertTrue(hasBase);
    }

    @Test
    public void testListResourceLoaderPaths() throws Exception {

        ModelNode op = Util.createEmptyOperation("list-resource-loader-paths", RESOURCE);
        op.get(MODULE).set("org.jboss.dmr");

        ModelNode response = getModelControllerClient().execute(op);
        List<ModelNode> hostResult = validateResponse(response).asList();
        Assert.assertTrue(hostResult.size() > 0);
        /* resource loader paths may come from maven repo
        for (ModelNode path : hostResult) {
            //result will different depending on if artifact or resource is in use
            Assert.assertTrue("Failed " + hostResult, path.asString().contains(LAYERS_BASE) || path.asString().matches(".*org.jboss.jboss-dmr.*"));
        }
        */
    }
}
