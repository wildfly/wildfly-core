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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    protected static final PathAddress SERVER_GROUP_MAIN_SERVER_GROUP = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");
    protected static final PathAddress JVM_DEFAULT = PathAddress.pathAddress(JVM, "default");
    protected static final PathAddress JVM_BYTEMAN = PathAddress.pathAddress(JVM, "byteman");
    protected static final PathAddress CORE_SERVICE_MANAGEMENT = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT);
    protected static final PathAddress SERVICE_MANAGEMENT_OPERATIONS = PathAddress.pathAddress(SERVICE, MANAGEMENT_OPERATIONS);

    private static DomainTestSupport testSupport;
    private static DomainClient masterClient;
    private static DomainLifecycleUtil masterLifecycleUtil;
    private static DomainLifecycleUtil slaveLifecycleUtil;

    private static List<String> slaveChildrenTypes;
    private static List<String> emptyAddressChildrenTypes;

    @BeforeClass
    public static void setupDomain() throws Exception {

        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(HostControllerBootOperationsTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml",
                "host-configs/host-master.xml",
                "host-configs/host-slave-main-three-without-jvm.xml"
        );

        testSupport = DomainTestSupport.create(configuration);

        masterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        slaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();

        testSupport.start();

        masterClient = masterLifecycleUtil.getDomainClient();

        ModelNode op;
        op = Util.createAddOperation(SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN));
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

        ModelNode result;
        op = Util.createEmptyOperation(READ_CHILDREN_TYPES_OPERATION, SLAVE_ADDR);
        result = DomainTestUtils.executeForResult(op, masterClient);
        slaveChildrenTypes = result.asList().stream().map(m -> m.asString()).collect(Collectors.toList());

        op = Util.createEmptyOperation(READ_CHILDREN_TYPES_OPERATION, PathAddress.EMPTY_ADDRESS);
        result = DomainTestUtils.executeForResult(op, masterClient);
        emptyAddressChildrenTypes = result.asList().stream().map(m -> m.asString()).collect(Collectors.toList());
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
        // The slave write lock is acquired at this time because the servers are starting, although they have not been registered in the domain yet
        // Read operations should pass, even those that use proxies because at this point, the servers are not registered, proxies does not exists yet

        checkReadOperations(false);

        //assert the server at this point reports STOPPED, which means it has not been registered yet in the domain
        Assert.assertTrue("server-three should be stopped at this point to validate this test conditions. Check if a previous read operation acquired the write lock or if the \"Delay Server Registration Request\" byteman rule needs more time sleeping the server registration request",
                DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER_MAIN_THREE), "server-state"), masterClient).asString().equals("STOPPED")
        );

        // Wait until the delayed server is registered in the domain
        DomainTestUtils.waitUntilState(masterClient, SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE), ServerStatus.STARTING.toString());

        // At this point server-three should be waiting before transitioning to STARTED, this gives us a window to test operations that are
        // likely to fail since the server is still starting, write operations should fail, read operations should pass except those executed with proxies enabled

        op = Util.getWriteAttributeOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(JVM_DEFAULT), "heap-size", "64m");
        ModelNode failureDescription = DomainTestUtils.executeForFailure(op, masterClient);
        Assert.assertTrue("The slave host does not return the expected error. Failure Description was:"+failureDescription, failureDescription.get("host-failure-descriptions").get("slave").asString().startsWith("WFLYCTL0379"));

        checkReadOperations(true);

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

    private void checkReadOperations(boolean skipServerDirectReads) throws IOException, MgmtOperationException {
        ModelNode op;

        // assert we are also able to read the Domain model recursively
        // this also test WFCORE-4596
        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
        addCommonReadOperationAttributes(op, skipServerDirectReads);
        DomainTestUtils.executeForResult(op, masterClient);

        // assert we are also able to read the HC slave model recursively
        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SLAVE_ADDR);
        op.get("recursive").set(true);
        DomainTestUtils.executeForResult(op, masterClient);

        // assert we are also able to read the server config of the server under test
        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE));
        addCommonReadOperationAttributes(op, skipServerDirectReads);
        DomainTestUtils.executeForResult(op, masterClient);

        // The following read operations could be redundant since the read information should have been already returned
        // by the above operations that were executed to read recursively. However, just in case to be more confident
        for(String childType : emptyAddressChildrenTypes) {
            op = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, PathAddress.EMPTY_ADDRESS);
            op.get("child-type").set(childType);
            addCommonReadOperationAttributes(op, skipServerDirectReads);

            DomainTestUtils.executeForResult(op, masterClient);
        }

        for(String childType : slaveChildrenTypes) {
            op = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, SLAVE_ADDR);
            op.get("child-type").set(childType);
            addCommonReadOperationAttributes(op, skipServerDirectReads);

            DomainTestUtils.executeForResult(op, masterClient);
        }

        // Check also read in composite operations
        List<ModelNode> steps;

        steps = prepareReadCompositeOperations(PathAddress.EMPTY_ADDRESS, emptyAddressChildrenTypes, skipServerDirectReads);
        op = createComposite(steps);
        DomainTestUtils.executeForResult(op, masterClient);

        steps.clear();
        steps = prepareReadCompositeOperations(SLAVE_ADDR, slaveChildrenTypes, skipServerDirectReads);

        op = createComposite(steps);
        DomainTestUtils.executeForResult(op, masterClient);

        steps = prepareReadCompositeOperations(PathAddress.EMPTY_ADDRESS, emptyAddressChildrenTypes, skipServerDirectReads);
        steps.addAll(prepareReadCompositeOperations(SLAVE_ADDR, slaveChildrenTypes, skipServerDirectReads));

        op = createComposite(steps);
        DomainTestUtils.executeForResult(op, masterClient);

        // check if we can read the server status
        op = Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE), "status");
        DomainTestUtils.executeForResult(op, masterClient);

        // WFCORE-4830
        // Besides of standard read operations, the following ones are allowed when a HC is starting
        op = Util.createEmptyOperation("read-boot-errors", SLAVE_ADDR.append(CORE_SERVICE_MANAGEMENT));
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createEmptyOperation("whoami", SLAVE_ADDR.append(CORE_SERVICE_MANAGEMENT));
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createEmptyOperation("find-non-progressing-operation", SLAVE_ADDR.append(CORE_SERVICE_MANAGEMENT).append(SERVICE_MANAGEMENT_OPERATIONS));
        DomainTestUtils.executeForResult(op, masterClient);
    }

    private ModelNode createComposite(List<ModelNode> steps) {
        ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode stepsNode = composite.get(STEPS);
        for (ModelNode step : steps) {
            stepsNode.add(step);
        }
        return composite;
    }

    private void addCommonReadOperationAttributes(ModelNode op, boolean skipServerDirectReads) {
        String operation = op.get(OP).asString();
        switch (operation) {
            case READ_RESOURCE_OPERATION: {
                op.get("include-aliases").set(true);
                op.get("include-undefined-metric-values").set(true);
            }
            case READ_CHILDREN_RESOURCES_OPERATION: {
                op.get("include-runtime").set(true);
                op.get("recursive").set(true);
                op.get("include-defaults").set(true);
                op.get("proxies").set(!skipServerDirectReads);
            }
        }
    }

    private List<ModelNode> prepareReadCompositeOperations(PathAddress address, List<String> childTypes, boolean skipServerDirectReads) {
        final List<ModelNode> steps = new ArrayList<>();
        ModelNode step;

        step = Util.createEmptyOperation(READ_RESOURCE_OPERATION, address);
        addCommonReadOperationAttributes(step, skipServerDirectReads);
        steps.add(step);

        for(String childType : childTypes) {
            if (skipServerDirectReads && childType.equals("server")) continue;
            step = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, address);
            addCommonReadOperationAttributes(step, skipServerDirectReads);
            step.get("child-type").set(childType);
            steps.add(step);
        }

        return steps;
    }
}
