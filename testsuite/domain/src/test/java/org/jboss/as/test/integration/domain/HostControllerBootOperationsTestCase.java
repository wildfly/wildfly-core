/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
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
    protected static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");
    protected static final PathAddress SERVER_CONFIG_MAIN_THREE = PathAddress.pathAddress(SERVER_CONFIG, "main-three");
    protected static final PathAddress SERVER_MAIN_THREE = PathAddress.pathAddress(SERVER, "main-three");
    protected static final PathAddress SERVER_GROUP_MAIN_SERVER_GROUP = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");
    protected static final PathAddress JVM_DEFAULT = PathAddress.pathAddress(JVM, "default");
    protected static final PathAddress JVM_BYTEMAN = PathAddress.pathAddress(JVM, "byteman");
    protected static final PathAddress CORE_SERVICE_MANAGEMENT = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT);
    protected static final PathAddress SERVICE_MANAGEMENT_OPERATIONS = PathAddress.pathAddress(SERVICE, MANAGEMENT_OPERATIONS);

    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static DomainLifecycleUtil primaryLifecycleUtil;
    private static DomainLifecycleUtil secondaryLifecycleUtil;

    private static List<String> secondaryChildrenTypes;
    private static List<String> emptyAddressChildrenTypes;

    private static final int SLEEP_TIME_MILLIS = 100;

    @BeforeClass
    public static void setupDomain() throws Exception {

        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(HostControllerBootOperationsTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml",
                "host-configs/host-primary.xml",
                "host-configs/host-secondary-main-three-without-jvm.xml"
        );

        testSupport = DomainTestSupport.create(configuration);

        primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        secondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();

        testSupport.start();

        primaryClient = primaryLifecycleUtil.getDomainClient();

        ModelNode op;
        op = Util.createAddOperation(SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN));
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createEmptyOperation("add-jvm-option", SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN));
        op.get("jvm-option").set("-Dorg.jboss.byteman.verbose=true");
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createEmptyOperation("add-jvm-option", SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN));
        op.get("jvm-option").set("-Djboss.modules.system.pkgs=org.jboss.byteman");
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createEmptyOperation("add-jvm-option", SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN));
        op.get("jvm-option").set("-Djava.security.policy==" + System.getProperty("java.io.tmpdir") + "/test-classes/byteman-scripts/byteman.policy");
        DomainTestUtils.executeForResult(op, primaryClient);

        String bytemanJavaAgent = System.getProperty("jboss.test.host.server.byteman.javaagent")+"DelayServerRegistrationAndRunningState.btm";
        op = Util.getWriteAttributeOperation(SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_BYTEMAN), "java-agent", bytemanJavaAgent);
        DomainTestUtils.executeForResult(op, primaryClient);

        ModelNode result;
        op = Util.createEmptyOperation(READ_CHILDREN_TYPES_OPERATION, SECONDARY_ADDR);
        result = DomainTestUtils.executeForResult(op, primaryClient);
        secondaryChildrenTypes = result.asList().stream().map(m -> m.asString()).collect(Collectors.toList());

        op = Util.createEmptyOperation(READ_CHILDREN_TYPES_OPERATION, PathAddress.EMPTY_ADDRESS);
        result = DomainTestUtils.executeForResult(op, primaryClient);
        emptyAddressChildrenTypes = result.asList().stream().map(m -> m.asString()).collect(Collectors.toList());
    }

    @AfterClass
    public static void shutdownDomain() {
        testSupport.close();
        testSupport = null;
        primaryClient = null;
        secondaryLifecycleUtil = null;
        primaryLifecycleUtil = null;
    }

    @Test
    public void testManagementOperationsWhenSecondaryHCisBooting() throws Exception {
        ModelNode op = Util.createEmptyOperation("reload", SECONDARY_ADDR);
        op.get(RESTART_SERVERS).set(true);
        DomainTestUtils.executeForResult(op, primaryClient);

        secondaryLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.STARTING);
        // At this point server-three should be waiting before being registered in the domain and HC is still booting up, this gives us a window
        // where we can test the operations that were failing and reporting errors in HAL on WFCORE-4283
        // The secondary write lock is acquired at this time because the servers are starting, although they have not been registered in the domain yet
        // Read operations should pass, even those that use proxies because at this point, the servers are not registered, proxies does not exist yet

        checkReadOperations(false);

        String message = "server-three should be stopped at this point to validate this test conditions. Check if a previous read operation acquired the write lock or if the \"Delay Server Registration Request\" byteman rule needs more time sleeping the server registration request";
        try {
            ModelNode result = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER_MAIN_THREE), "server-state"), primaryClient);
            //assert the server at this point reports STOPPED, which means it has not been registered yet in the domain
            Assert.assertTrue(message, result.asString().equals("STOPPED"));
        } catch (MgmtOperationException e) {
            ModelNode failed = e.getResult();
            if (failed.get("failure-description").asString().startsWith("WFLYCTL0379")) {
                // Server-three was already registered in the domain and it is still starting
                Assert.fail(message);
            }
        }

        // Wait until the delayed server is registered in the domain. As soon it is registered, it will be paused on the booting phase due to byteman, if we read the server state
        // directly, we will get a message telling us the server is starting
        waitUntilServerRegisteredButStarting(primaryClient, SECONDARY_ADDR.append(SERVER_MAIN_THREE));

        // At this point server-three should be waiting before transitioning to STARTED, this gives us a window to test operations that are
        // likely to fail since the server is still starting, write operations should fail, read operations should pass except those executed with proxies enabled

        op = Util.getWriteAttributeOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(JVM_DEFAULT), "heap-size", "64m");
        ModelNode failureDescription = DomainTestUtils.executeForFailure(op, primaryClient);
        Assert.assertTrue("The secondary host does not return the expected error. Failure Description was:"+failureDescription, failureDescription.get("host-failure-descriptions").get("secondary").asString().startsWith("WFLYDC0098"));

        checkReadOperations(true);

        // assert server is still starting at this moment
        Assert.assertTrue("server-three should be starting at this point to validate this test conditions. Check if the \"Delay Server Started Request\" byteman rule needs more time sleeping the server started request",
                DomainTestUtils.executeForFailure(Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER_MAIN_THREE), "server-state"), primaryClient).asString().startsWith("WFLYCTL0379")
        );

        // Wait for all the servers until the servers are started and HC is running
        secondaryLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.RUNNING);
        DomainTestUtils.waitUntilState(primaryClient, SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE), ServerStatus.STARTED.toString());

        // write operation should success at this moment
        op = Util.getWriteAttributeOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(JVM_DEFAULT), "heap-size", "64m");
        DomainTestUtils.executeForResult(op, primaryClient);
    }

    private void checkReadOperations(boolean skipServerDirectReads) throws IOException, MgmtOperationException {
        long start = System.currentTimeMillis();
        ModelNode op;

        // assert we are also able to read the Domain model recursively
        // this also test WFCORE-4596
        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
        addCommonReadOperationAttributes(op, skipServerDirectReads);
        DomainTestUtils.executeForResult(op, primaryClient);

        // assert we are also able to read the HC secondary model recursively
        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SECONDARY_ADDR);
        op.get("recursive").set(true);
        DomainTestUtils.executeForResult(op, primaryClient);

        // assert we are also able to read the server config of the server under test
        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE));
        addCommonReadOperationAttributes(op, skipServerDirectReads);
        DomainTestUtils.executeForResult(op, primaryClient);

        // The following read operations could be redundant since the read information should have been already returned
        // by the above operations that were executed to read recursively. However, just in case to be more confident
        for(String childType : emptyAddressChildrenTypes) {
            op = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, PathAddress.EMPTY_ADDRESS);
            op.get("child-type").set(childType);
            addCommonReadOperationAttributes(op, skipServerDirectReads);

            DomainTestUtils.executeForResult(op, primaryClient);
        }

        for(String childType : secondaryChildrenTypes) {
            op = Util.createEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, SECONDARY_ADDR);
            op.get("child-type").set(childType);
            addCommonReadOperationAttributes(op, skipServerDirectReads);

            DomainTestUtils.executeForResult(op, primaryClient);
        }

        // Check also read in composite operations
        List<ModelNode> steps;

        steps = prepareReadCompositeOperations(PathAddress.EMPTY_ADDRESS, emptyAddressChildrenTypes, skipServerDirectReads);
        op = createComposite(steps);
        DomainTestUtils.executeForResult(op, primaryClient);

        steps.clear();
        steps = prepareReadCompositeOperations(SECONDARY_ADDR, secondaryChildrenTypes, skipServerDirectReads);

        op = createComposite(steps);
        DomainTestUtils.executeForResult(op, primaryClient);

        steps = prepareReadCompositeOperations(PathAddress.EMPTY_ADDRESS, emptyAddressChildrenTypes, skipServerDirectReads);
        steps.addAll(prepareReadCompositeOperations(SECONDARY_ADDR, secondaryChildrenTypes, skipServerDirectReads));

        op = createComposite(steps);
        DomainTestUtils.executeForResult(op, primaryClient);

        // check if we can read the server status
        op = Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE), "status");
        DomainTestUtils.executeForResult(op, primaryClient);

        // WFCORE-4830
        // Besides of standard read operations, the following ones are allowed when a HC is starting
        op = Util.createEmptyOperation("read-boot-errors", SECONDARY_ADDR.append(CORE_SERVICE_MANAGEMENT));
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createEmptyOperation("whoami", SECONDARY_ADDR.append(CORE_SERVICE_MANAGEMENT));
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createEmptyOperation("find-non-progressing-operation", SECONDARY_ADDR.append(CORE_SERVICE_MANAGEMENT).append(SERVICE_MANAGEMENT_OPERATIONS));
        DomainTestUtils.executeForResult(op, primaryClient);

        long end = System.currentTimeMillis();
        long duration = end - start;
        System.out.println("Read Operations took " + TimeUnit.MILLISECONDS.toSeconds(duration) + " seconds.");
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

    private static void waitUntilServerRegisteredButStarting(final ModelControllerClient client, final PathAddress serverAddress) throws IOException, MgmtOperationException {
        final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TimeoutUtil.adjust(60));
        for(;;) {
            final long remaining = deadline - System.currentTimeMillis();
            if(remaining <= 0) {
                break;
            }
            try {
                ModelNode result = DomainTestUtils.executeForFailure(Util.getReadAttributeOperation(serverAddress, "server-state"), client);
                if (result.asString().startsWith("WFLYCTL0379")) {
                    return;
                }
                break;
            } catch (MgmtOperationException e) {
                // ignore, not an error yet
            }
            try {
                TimeUnit.MILLISECONDS.sleep(SLEEP_TIME_MILLIS);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        ModelNode result = DomainTestUtils.executeForFailure(Util.getReadAttributeOperation(serverAddress, "server-state"), client);
        Assert.assertTrue(result.asString().startsWith("WFLYCTL0379"));
    }
}
