/*
 * Copyright 2020 Red Hat, Inc.
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests an slave is able to be registered in a domain meanwhile the Domain Controller is starting its servers.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public class SlaveRegistrationTestCase {
    protected static final PathAddress MASTER_ADDR = PathAddress.pathAddress(HOST, "master");
    protected static final PathAddress SLAVE_ADDR = PathAddress.pathAddress(HOST, "slave");
    protected static final PathAddress SERVER_CONFIG_MAIN_ONE = PathAddress.pathAddress(SERVER_CONFIG, "main-one");
    protected static final PathAddress SERVER_CONFIG_MAIN_TWO = PathAddress.pathAddress(SERVER_CONFIG, "main-two");
    protected static final PathAddress SERVER_MAIN_TWO = PathAddress.pathAddress(SERVER, "main-two");
    protected static final PathAddress JVM_DEFAULT = PathAddress.pathAddress(JVM, "default");

    private static DomainTestSupport testSupport;
    private static DomainClient masterClient;
    private static DomainClient slaveClient;
    private static DomainLifecycleUtil masterLifecycleUtil;
    private static DomainLifecycleUtil slaveLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {

        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(SlaveRegistrationTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml",
                "host-configs/host-master.xml",
                "host-configs/host-slave.xml"
        );

        testSupport = DomainTestSupport.create(configuration);

        masterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        slaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();

        testSupport.start();

        masterClient = masterLifecycleUtil.getDomainClient();
        slaveClient = slaveLifecycleUtil.getDomainClient();

        ModelNode op;

        op = Util.createEmptyOperation("add-jvm-option", MASTER_ADDR.append(SERVER_CONFIG_MAIN_TWO).append(JVM_DEFAULT));
        op.get("jvm-option").set("-Dorg.jboss.byteman.verbose=true");
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createEmptyOperation("add-jvm-option", MASTER_ADDR.append(SERVER_CONFIG_MAIN_TWO).append(JVM_DEFAULT));
        op.get("jvm-option").set("-Djboss.modules.system.pkgs=org.jboss.byteman");
        DomainTestUtils.executeForResult(op, masterClient);

        String bytemanJavaAgent = System.getProperty("jboss.test.host.server.byteman.javaagent")+"DelayServerRegistration.btm";
        op = Util.getWriteAttributeOperation(MASTER_ADDR.append(SERVER_CONFIG_MAIN_TWO).append(JVM_DEFAULT), "java-agent", bytemanJavaAgent);
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.getWriteAttributeOperation(MASTER_ADDR.append(SERVER_CONFIG_MAIN_TWO), "auto-start", true);
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
    public void testSlaveRegistrationWhenDcIsStarting() throws Exception {
        ModelNode op = Util.createEmptyOperation("reload", MASTER_ADDR);
        op.get(RESTART_SERVERS).set(true);
        DomainTestUtils.executeForResult(op, masterClient);

        masterLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.STARTING);

        // wait until main-one is starting, main-two is going to started but it will be blocked by the byteman rule.
        // This scenario will put the DC boot phase in the middle of server starting
        DomainTestUtils.waitUntilState(masterClient, MASTER_ADDR.append(SERVER_CONFIG_MAIN_ONE), "STARTED");

        op = Util.createEmptyOperation("reload", SLAVE_ADDR);
        op.get(RESTART_SERVERS).set(false);
        DomainTestUtils.executeForResult(op, slaveClient);

        slaveLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.RUNNING);

        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SLAVE_ADDR);
        DomainTestUtils.executeForResult(op, masterClient);

        //assert the main-one server at this point reports STOPPED, which means it has not been registered yet in the domain
        Assert.assertTrue("main-two should be stopped at this point to validate this test conditions.",
                DomainTestUtils.executeForResult(Util.getReadAttributeOperation(MASTER_ADDR.append(SERVER_MAIN_TWO), "server-state"), masterClient).asString().equals("STOPPED")
        );

        // wait until DC full start
        masterLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.RUNNING);
    }
}
