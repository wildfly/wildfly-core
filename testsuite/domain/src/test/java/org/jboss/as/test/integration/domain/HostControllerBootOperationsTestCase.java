/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;

import java.io.IOException;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Validates the execution of management operations when a HC is starting.
 * <p>
 * The test case uses two byteman rules to delay the Server Registration Request, which delays the server transition
 * from STOPPED to STARTING, and the ServerService#finishBoot, which delays the server transition from STARTING to STARTED
 * and keeps the server booting flag set.
 * <p>
 * The test validates that read resource operations executed from the DC success when the servers are in its booting phase.
 * Write operations should be rejected when the servers are in STARTING state.
 * <p>
 * Notice that some read operations at server root level are still being rejected, for example /host=x/server=y:read-attribute(name=status-server)
 * That could be improved if it is necessary. As an alternative, we can use /host=x/server-config=y:read-attribute(name=status)
 * which will read the server status from the HC server inventory, without the need to send it to the proxy server.
 *
 * @author Yeray Borges
 */
public class HostControllerBootOperationsTestCase {
    protected static final PathAddress SLAVE_ADDR = PathAddress.pathAddress(HOST, "slave");
    protected static final PathAddress SERVER_CONFIG_MAIN_THREE = PathAddress.pathAddress(SERVER_CONFIG, "main-three");
    protected static final PathAddress SERVER_MAIN_THREE = PathAddress.pathAddress(SERVER, "main-three");
    protected static final PathAddress CORE_SERVICE_MANAGEMENT = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT);
    protected static final PathAddress SERVER_GROUP_MAIN_SERVER_GROUP = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");
    protected static final PathAddress JVM_DEFAULT = PathAddress.pathAddress(JVM, "default");
    protected static final PathAddress JVM_BYTEMAN = PathAddress.pathAddress(JVM, "byteman");

    private static DomainTestSupport testSupport;
    private static DomainClient masterClient;
    private static DomainLifecycleUtil masterLifecycleUtil;
    private static DomainLifecycleUtil slaveLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {

        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(OperationTimeoutTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml",
                "host-configs/host-master.xml",
                "host-configs/host-slave-main-three-without-jvm.xml"
        );

        testSupport = DomainTestSupport.create(configuration);

        masterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        slaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();

        testSupport.start();

        masterClient = masterLifecycleUtil.getDomainClient();
        ModelNode op = Util.createAddOperation(SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN));
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createEmptyOperation("add-jvm-option", SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN));
        op.get("jvm-option").set("-Dorg.jboss.byteman.verbose=true");
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createEmptyOperation("add-jvm-option", SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN));
        op.get("jvm-option").set("-Djboss.modules.system.pkgs=org.jboss.byteman");
        DomainTestUtils.executeForResult(op, masterClient);

        String bytemanJavaAgent = System.getProperty("jboss.test.host.server.byteman.javaagent")+"DelayServerRegistrationAndRunningState.btm";
        op = Util.getWriteAttributeOperation(SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN), "java-agent", bytemanJavaAgent);
        DomainTestUtils.executeForResult(op, masterClient);
    }

    @AfterClass
    public static void shutdownDomain() {
        testSupport.close();
        testSupport = null;
        masterClient = null;
        slaveLifecycleUtil = null;
        masterLifecycleUtil = null;
    }

    @Test
    public void testManagementOperationsWhenSlaveHCisBooting() throws Exception {
        ModelNode op = Util.createEmptyOperation("reload", SLAVE_ADDR);
        op.get(RESTART_SERVERS).set(true);
        DomainTestUtils.executeForResult(op, masterClient);

        slaveLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.STARTING);
        // At this point server-three should be waiting before being registered in the domain and HC is still booting up, this gives us a window
        // where we can tests the operations that were failing and reporting errors in HAL on WFCORE-4283

        checkReadOperations();

        //assert the server at this point reports STOPPED, which means it has not been registered yet in the domain
        Assert.assertTrue("server-three should be stopped at this point to validate this test conditions. Check if a previous read operation acquired the write lock or if the \"Delay Server Registration Request\" byteman rule needs more time sleeping the server registration request",
                DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER_MAIN_THREE), "server-state"), masterClient).asString().equals("STOPPED")
        );

        // Wait until the delayed server is registered in the domain
        DomainTestUtils.waitUntilState(masterClient, SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE), ServerStatus.STARTING.toString());

        // At this point server-three should be waiting before transitioning to STARTED, this gives us a window to test operations that are
        // likely to fail since the server is still starting, write operations should fail, read operations should pass

        op = Util.getWriteAttributeOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(JVM_DEFAULT), "heap-size", "64m");
        ModelNode failureDescription = DomainTestUtils.executeForFailure(op, masterClient);
        Assert.assertTrue("The slave host does not return the expected error. Failure Description was:"+failureDescription, failureDescription.get("host-failure-descriptions").get("slave").asString().startsWith("WFLYDC0098"));

        checkReadOperations();

        // assert server is still starting at this moment
        Assert.assertTrue("server-three should be starting at this point to validate this test conditions. Check if the \"Delay Server Started Request\" byteman rule needs more time sleeping the server started request",
                DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE), "status"), masterClient).asString().equals(ServerStatus.STARTING.toString())
        );

        // Wait for all the servers until they are started and HC is running
        slaveLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.RUNNING);
        DomainTestUtils.waitUntilState(masterClient, SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE), ServerStatus.STARTED.toString());

        // write operation should success at this moment
        op = Util.getWriteAttributeOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(JVM_DEFAULT), "heap-size", "64m");
        DomainTestUtils.executeForResult(op, masterClient);
    }

    private void checkReadOperations() throws IOException, MgmtOperationException {
        // assert we are able to run the operation under test :read-child-resources
        ModelNode op  = Util.createEmptyOperation("read-children-resources", PathAddress.EMPTY_ADDRESS);
        op.get("child-type").set("host");
        op.get("recursive").set(true);
        DomainTestUtils.executeForResult(op, masterClient);

        // assert we are also able to read the HC slave model recursively
        op = Util.createEmptyOperation("read-resource", SLAVE_ADDR);
        op.get("recursive").set(true);
        DomainTestUtils.executeForResult(op, masterClient);

        // assert we are also able to read the Domain model recursively
        op = Util.createEmptyOperation("read-resource", PathAddress.EMPTY_ADDRESS);
        op.get("recursive").set(true);
        DomainTestUtils.executeForResult(op, masterClient);

        // assert we are also able to read the Domain model recursively
        op = Util.createEmptyOperation("read-children-resources", CORE_SERVICE_MANAGEMENT);
        op.get("child-type").set("access");
        op.get("recursive-depth").set("1");
        op.get("include-runtime").set("true");
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE), "status");
        DomainTestUtils.executeForResult(op, masterClient);
    }
}
