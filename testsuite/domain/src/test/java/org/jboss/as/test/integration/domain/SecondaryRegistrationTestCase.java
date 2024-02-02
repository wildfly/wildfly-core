/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
 * Tests a secondary is able to get registered in a domain meanwhile the Domain Controller is starting its servers.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public class SecondaryRegistrationTestCase {
    protected static final PathAddress PRIMARY_ADDR = PathAddress.pathAddress(HOST, "primary");
    protected static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");
    protected static final PathAddress SERVER_CONFIG_MAIN_ONE = PathAddress.pathAddress(SERVER_CONFIG, "main-one");
    protected static final PathAddress SERVER_CONFIG_MAIN_TWO = PathAddress.pathAddress(SERVER_CONFIG, "main-two");
    protected static final PathAddress SERVER_MAIN_TWO = PathAddress.pathAddress(SERVER, "main-two");
    protected static final PathAddress JVM_DEFAULT = PathAddress.pathAddress(JVM, "default");

    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static DomainClient secondaryClient;
    private static DomainLifecycleUtil primaryLifecycleUtil;
    private static DomainLifecycleUtil secondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {

        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(SecondaryRegistrationTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml",
                "host-configs/host-primary.xml",
                "host-configs/host-secondary.xml"
        );

        testSupport = DomainTestSupport.create(configuration);

        primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        secondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();

        testSupport.start();

        primaryClient = primaryLifecycleUtil.getDomainClient();
        secondaryClient = secondaryLifecycleUtil.getDomainClient();

        ModelNode op;

        op = Util.createEmptyOperation("add-jvm-option", PRIMARY_ADDR.append(SERVER_CONFIG_MAIN_TWO).append(JVM_DEFAULT));
        op.get("jvm-option").set("-Dorg.jboss.byteman.verbose=true");
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createEmptyOperation("add-jvm-option", PRIMARY_ADDR.append(SERVER_CONFIG_MAIN_TWO).append(JVM_DEFAULT));
        op.get("jvm-option").set("-Djboss.modules.system.pkgs=org.jboss.byteman");
        DomainTestUtils.executeForResult(op, primaryClient);

        String bytemanJavaAgent = System.getProperty("jboss.test.host.server.byteman.javaagent")+"DelayServerRegistration.btm";
        op = Util.getWriteAttributeOperation(PRIMARY_ADDR.append(SERVER_CONFIG_MAIN_TWO).append(JVM_DEFAULT), "java-agent", bytemanJavaAgent);
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.getWriteAttributeOperation(PRIMARY_ADDR.append(SERVER_CONFIG_MAIN_TWO), "auto-start", true);
        DomainTestUtils.executeForResult(op, primaryClient);

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
    public void testSecondaryRegistrationWhenDcIsStarting() throws Exception {
        ModelNode op = Util.createEmptyOperation("reload", PRIMARY_ADDR);
        op.get(RESTART_SERVERS).set(true);
        DomainTestUtils.executeForResult(op, primaryClient);

        primaryLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.STARTING);

        // wait until main-one is starting, main-two is going to started but it will be blocked by the byteman rule.
        // This scenario will put the DC boot phase in the middle of server starting
        DomainTestUtils.waitUntilState(primaryClient, PRIMARY_ADDR.append(SERVER_CONFIG_MAIN_ONE), "STARTED");

        op = Util.createEmptyOperation("reload", SECONDARY_ADDR);
        op.get(RESTART_SERVERS).set(false);
        DomainTestUtils.executeForResult(op, secondaryClient);

        secondaryLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.RUNNING);

        op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, SECONDARY_ADDR);
        DomainTestUtils.executeForResult(op, primaryClient);

        //assert the main-one server at this point reports STOPPED, which means it has not been registered yet in the domain
        Assert.assertTrue("main-two should be stopped at this point to validate this test conditions.",
                DomainTestUtils.executeForResult(Util.getReadAttributeOperation(PRIMARY_ADDR.append(SERVER_MAIN_TWO), "server-state"), primaryClient).asString().equals("STOPPED")
        );

        // wait until DC full start
        primaryLifecycleUtil.awaitHostController(System.currentTimeMillis(), ControlledProcessState.State.RUNNING);
    }
}
