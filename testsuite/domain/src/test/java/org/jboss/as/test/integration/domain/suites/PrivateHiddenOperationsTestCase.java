/*
Copyright 2017 Red Hat, Inc.

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

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.optypes.OpTypesExtension;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * Tests invocations of private and hidden operations.
 *
 * @author Brian Stansberry
 */
public class PrivateHiddenOperationsTestCase {

    private static final PathAddress MASTER = PathAddress.pathAddress(ModelDescriptionConstants.HOST, "master");
    private static final PathAddress SLAVE = PathAddress.pathAddress(ModelDescriptionConstants.HOST, "slave");

    private static final PathAddress EXT = PathAddress.pathAddress("extension", OpTypesExtension.EXTENSION_NAME);
    private static final PathAddress PROFILE = PathAddress.pathAddress("profile", "default");
    private static final PathElement SUBSYSTEM = PathElement.pathElement("subsystem", OpTypesExtension.SUBSYSTEM_NAME);
    private static final PathElement MAIN_ONE = PathElement.pathElement("server", "main-one");
    private static final PathElement MAIN_THREE = PathElement.pathElement("server", "main-three");

    private static DomainTestSupport testSupport;
    private static ManagementClient managementClient;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(PrivateHiddenOperationsTestCase.class.getSimpleName());
        DomainClient masterClient = testSupport.getDomainMasterLifecycleUtil().getDomainClient();
        managementClient = new ManagementClient(masterClient, TestSuiteEnvironment.getServerAddress(), 9090, "remoting+http");


        ExtensionUtils.createExtensionModule(OpTypesExtension.EXTENSION_NAME, OpTypesExtension.class,
                EmptySubsystemParser.class.getPackage());

        executeOp(Util.createAddOperation(EXT), SUCCESS);
        executeOp(Util.createAddOperation(PROFILE.append(SUBSYSTEM)), SUCCESS);

        executeOp(Util.createAddOperation(MASTER.append(EXT)), SUCCESS);
        executeOp(Util.createAddOperation(MASTER.append(SUBSYSTEM)), SUCCESS);

        executeOp(Util.createAddOperation(SLAVE.append(EXT)), SUCCESS);
        executeOp(Util.createAddOperation(SLAVE.append(SUBSYSTEM)), SUCCESS);
    }

    @AfterClass
    public static void tearDownDomain() throws IOException {
        Throwable t = null;
        List<ModelNode> ops = new ArrayList<>();
        ops.add(Util.createRemoveOperation(MASTER.append(SUBSYSTEM)));
        ops.add(Util.createRemoveOperation(MASTER.append(EXT)));
        ops.add(Util.createRemoveOperation(SLAVE.append(SUBSYSTEM)));
        ops.add(Util.createRemoveOperation(SLAVE.append(EXT)));
        ops.add(Util.createRemoveOperation(PROFILE.append(SUBSYSTEM)));
        ops.add(Util.createRemoveOperation(EXT));
        for (ModelNode op : ops) {
            try {
                executeOp(op, SUCCESS);
            } catch (IOException | AssertionError e) {
                if (t == null) {
                    t = e;
                }
            }
        }

        ExtensionUtils.deleteExtensionModule(OpTypesExtension.EXTENSION_NAME);

        testSupport = null;
        managementClient = null;
        DomainTestSuite.stopSupport();

        if (t instanceof IOException) {
            throw (IOException) t;
        } else if (t instanceof AssertionError) {
            throw (Error) t;
        }
    }

    @Test
    public void testDomainLevel() throws IOException {
        testPrivateHiddenOps(PROFILE, false);
    }

    @Test
    public void testDomainCallsPrivateServer() throws IOException {
        testPrivateOp(PROFILE, true, SUCCESS);
    }

    @Test
    public void testMasterServer() throws IOException {
        testPrivateHiddenOps(MASTER.append(MAIN_ONE), true);
    }

    @Test
    public void testSlaveServer() throws IOException {
        testPrivateHiddenOps(SLAVE.append(MAIN_THREE), true);
    }

    @Test
    public void testMasterHC() throws IOException {
        testPrivateHiddenOps(MASTER, false);
    }

    @Test
    public void testSlaveHC() throws IOException {
        testPrivateHiddenOps(SLAVE, false);
    }

    private void testPrivateHiddenOps(PathAddress base, boolean domain) throws IOException {
        testHiddenOp(base, domain);
        testPrivateOp(base, domain, FAILED);
    }

    private void testHiddenOp(PathAddress base, boolean domain) throws IOException {
        PathAddress target = base.append(SUBSYSTEM);
        String prefix = domain ? "domain-" : "";
        executeOp(Util.createEmptyOperation(prefix + "hidden", target), SUCCESS);
    }

    private void testPrivateOp(PathAddress base, boolean domain, String outcome) throws IOException {
        PathAddress target = base.append(SUBSYSTEM);
        String prefix = domain ? "domain-" : "";
        executeOp(Util.createEmptyOperation(prefix + "private", target), outcome);
    }

    private static void executeOp(ModelNode op, String outcome) throws IOException {
        ModelNode response = managementClient.getControllerClient().execute(op);
        assertTrue(response.toString(), response.hasDefined(OUTCOME));
        assertEquals(response.toString(), outcome, response.get(OUTCOME).asString());
    }
}
