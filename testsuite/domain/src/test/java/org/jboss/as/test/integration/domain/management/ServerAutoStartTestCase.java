/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.domain.management;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTO_START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP_SERVERS;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.waitUntilState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Emanuel Muckenhuber
 */
public class ServerAutoStartTestCase {

    private static final String STOPPED_EXT = ".stopped";
    private static final String STARTED_EXT = ".started";

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;
    private static final ModelNode hostPrimary = new ModelNode();
    private static final ModelNode hostSecondary = new ModelNode();
    private static final ModelNode mainOne = new ModelNode();
    private static final ModelNode mainTwo = new ModelNode();
    private static final ModelNode mainThree = new ModelNode();
    private static final ModelNode mainFour = new ModelNode();
    private static final ModelNode otherOne = new ModelNode();
    private static final ModelNode otherTwo = new ModelNode();
    private static final ModelNode otherThree = new ModelNode();
    private static final ModelNode otherFour = new ModelNode();
    private static final Path autoStartPrimaryDataDir
            = DomainTestSupport.getHostDir(ServerAutoStartTestCase.class.getSimpleName(), "primary").toPath()
            .resolve("data")
            .resolve("auto-start");
    private static final Path autoStartSecondaryDataDir
            = DomainTestSupport.getHostDir(ServerAutoStartTestCase.class.getSimpleName(), "secondary").toPath()
            .resolve("data")
            .resolve("auto-start");

    static {
        // (host=primary)
        hostPrimary.add("host", "primary");
        // (host=primary),(server-config=main-one)
        mainOne.add("host", "primary");
        mainOne.add("server-config", "main-one");
        // (host=primary),(server-config=main-two)
        mainTwo.add("host", "primary");
        mainTwo.add("server-config", "main-two");
        // (host=primary),(server-config=other-one)
        otherOne.add("host", "primary");
        otherOne.add("server-config", "other-one");
        // (host=primary),(server-config=other-two)
        otherTwo.add("host", "primary");
        otherTwo.add("server-config", "other-two");

        // (host=secondary)
        hostSecondary.add("host", "secondary");
        // (host=secondary),(server-config=main-three)
        mainThree.add("host", "secondary");
        mainThree.add("server-config", "main-three");
        // (host=secondary),(server-config=main-four)
        mainFour.add("host", "secondary");
        mainFour.add("server-config", "main-four");
        // (host=secondary),(server-config=other-three)
        otherThree.add("host", "secondary");
        otherThree.add("server-config", "other-three");
        // (host=secondary),(server-config=other-four)
        otherFour.add("host", "secondary");
        otherFour.add("server-config", "other-four");

    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.createAndStartSupport(DomainTestSupport.Configuration.create(ServerAutoStartTestCase.class.getSimpleName(),
                "domain-configs/domain-minimal.xml", "host-configs/host-primary-auto-start.xml", "host-configs/host-secondary-auto-start.xml"));
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            MatcherAssert.assertThat("testSupport", testSupport, is(notNullValue()));
            testSupport.close();
        } finally {
            domainPrimaryLifecycleUtil = null;
            domainSecondaryLifecycleUtil = null;
            testSupport = null;
        }
    }

    @Test
    public void testDomainLifecycleMethods() throws Throwable {

        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();
        executeLifecycleOperation(client, START_SERVERS);
        waitUntilState(client, "primary", "main-one", "STARTED");
        waitUntilState(client, "primary", "main-two", "STARTED");
        waitUntilState(client, "primary", "other-one", "STARTED");
        waitUntilState(client, "primary", "other-two", "STARTED");
        waitUntilState(client, "secondary", "main-three", "STARTED");
        waitUntilState(client, "secondary", "main-four", "STARTED");
        waitUntilState(client, "secondary", "other-three", "STARTED");
        waitUntilState(client, "secondary", "other-four", "STARTED");
        assertAutoStartStatus(client, mainOne, true);
        assertAutoStartStatus(client, mainTwo, true);
        assertAutoStartStatus(client, otherOne, true);
        assertAutoStartStatus(client, otherTwo, false);
        assertAutoStartStatus(client, mainThree, true);
        assertAutoStartStatus(client, mainFour, true);
        assertAutoStartStatus(client, otherThree, true);
        assertAutoStartStatus(client, otherFour, false);
        assertAutoStartNotUpdated(autoStartPrimaryDataDir, "main-one");
        assertAutoStartUpdated(autoStartPrimaryDataDir, "main-two", true);
        assertAutoStartUpdated(autoStartPrimaryDataDir, "other-one", true);
        assertAutoStartNotUpdated(autoStartPrimaryDataDir, "other-two");
        assertAutoStartNotUpdated(autoStartSecondaryDataDir, "main-three");
        assertAutoStartUpdated(autoStartSecondaryDataDir, "main-four", true);
        assertAutoStartUpdated(autoStartSecondaryDataDir, "other-three", true);
        assertAutoStartNotUpdated(autoStartSecondaryDataDir, "other-four");

        executeLifecycleOperation(client, STOP_SERVERS);
        //When stopped auto-start=true -> STOPPED, auto-start=false -> DISABLED
        waitUntilState(client, "primary", "main-one", "STOPPED");
        waitUntilState(client, "primary", "main-two", "DISABLED");
        waitUntilState(client, "primary", "other-one", "DISABLED");
        waitUntilState(client, "primary", "other-two", "DISABLED");
        waitUntilState(client, "secondary", "main-three", "STOPPED");
        waitUntilState(client, "secondary", "main-four", "DISABLED");
        waitUntilState(client, "secondary", "other-three", "DISABLED");
        waitUntilState(client, "secondary", "other-four", "DISABLED");
        assertAutoStartStatus(client, mainOne, true);
        assertAutoStartStatus(client, mainTwo, false);
        assertAutoStartStatus(client, otherOne, false);
        assertAutoStartStatus(client, otherTwo, false);
        assertAutoStartStatus(client, mainThree, true);
        assertAutoStartStatus(client, mainFour, false);
        assertAutoStartStatus(client, otherThree, false);
        assertAutoStartStatus(client, otherFour, false);
        assertAutoStartNotUpdated(autoStartPrimaryDataDir, "main-one");
        assertAutoStartUpdated(autoStartPrimaryDataDir, "main-two", false);
        assertAutoStartUpdated(autoStartPrimaryDataDir, "other-one", false);
        assertAutoStartNotUpdated(autoStartPrimaryDataDir, "other-two");
        assertAutoStartNotUpdated(autoStartSecondaryDataDir, "main-three");
        assertAutoStartUpdated(autoStartSecondaryDataDir, "main-four", false);
        assertAutoStartUpdated(autoStartSecondaryDataDir, "other-three", false);
        assertAutoStartNotUpdated(autoStartSecondaryDataDir, "other-four");

        executeLifecycleOperation(client, START_SERVERS);
        waitUntilState(client, "primary", "main-one", "STARTED");
        waitUntilState(client, "primary", "main-two", "STARTED");
        waitUntilState(client, "primary", "other-one", "STARTED");
        waitUntilState(client, "primary", "other-two", "STARTED");
        waitUntilState(client, "secondary", "main-three", "STARTED");
        waitUntilState(client, "secondary", "main-four", "STARTED");
        waitUntilState(client, "secondary", "other-three", "STARTED");
        waitUntilState(client, "secondary", "other-four", "STARTED");
        assertAutoStartStatus(client, mainOne, true);
        assertAutoStartStatus(client, mainTwo, true);
        assertAutoStartStatus(client, otherOne, true);
        assertAutoStartStatus(client, otherTwo, false);
        assertAutoStartNotUpdated(autoStartPrimaryDataDir, "main-one");
        assertAutoStartUpdated(autoStartPrimaryDataDir, "main-two", true);
        assertAutoStartUpdated(autoStartPrimaryDataDir, "other-one", true);
        assertAutoStartNotUpdated(autoStartPrimaryDataDir, "other-two");
        assertAutoStartNotUpdated(autoStartSecondaryDataDir, "main-three");
        assertAutoStartUpdated(autoStartSecondaryDataDir, "main-four", true);
        assertAutoStartUpdated(autoStartSecondaryDataDir, "other-three", true);
        assertAutoStartNotUpdated(autoStartSecondaryDataDir, "other-four");

        domainPrimaryLifecycleUtil.stop();
        assertAutoStartNotUpdated(autoStartPrimaryDataDir, "main-one");
        assertAutoStartUpdated(autoStartPrimaryDataDir, "main-two", true);
        assertAutoStartUpdated(autoStartPrimaryDataDir, "other-one", true);
        assertAutoStartNotUpdated(autoStartPrimaryDataDir, "other-two");

        domainPrimaryLifecycleUtil.start();
        client = domainPrimaryLifecycleUtil.getDomainClient();
        waitUntilState(client, "primary", "main-one", "STARTED");
        waitUntilState(client, "primary", "main-two", "STARTED");
        waitUntilState(client, "primary", "other-one", "STARTED");
        //WFCORE-905
        executeFailingBatch(client);
    }

    private void assertAutoStartStatus(final ModelControllerClient client, ModelNode address, boolean autostart) throws IOException {
        final ModelNode operation = Operations.createReadAttributeOperation(address, AUTO_START);
        ModelNode result = validateResponse(client.execute(operation));
        MatcherAssert.assertThat(result.asBoolean(), is(autostart));
    }

    private void assertAutoStartNotUpdated(Path dir, String serverName) throws IOException {
        Path startedFile = dir.resolve(serverName + STARTED_EXT);
        Path stoppedFile = dir.resolve(serverName + STOPPED_EXT);
        MatcherAssert.assertThat(Files.exists(startedFile), is(false));
        MatcherAssert.assertThat(Files.exists(stoppedFile), is(false));
    }

    private void assertAutoStartUpdated(Path dir, String serverName, boolean autostart) throws IOException {
        Path startedFile = dir.resolve(serverName + STARTED_EXT);
        Path stoppedFile = dir.resolve(serverName + STOPPED_EXT);
        if (autostart) {
            MatcherAssert.assertThat(Files.exists(startedFile), is(true));
            MatcherAssert.assertThat(Files.exists(stoppedFile), is(false));
        } else {
            MatcherAssert.assertThat(Files.exists(startedFile), is(false));
            MatcherAssert.assertThat(Files.exists(stoppedFile), is(true));
        }
    }

    private void executeLifecycleOperation(final ModelControllerClient client, String opName) throws IOException {
        executeLifecycleOperation(client, null, opName);
    }

    private void executeLifecycleOperation(final ModelControllerClient client, String groupName, String opName) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(opName);
        if (groupName == null) {
            operation.get(OP_ADDR).setEmptyList();
        } else {
            operation.get(OP_ADDR).add(SERVER_GROUP, groupName);
        }
        validateResponse(client.execute(operation));
    }

    private void executeFailingBatch(final ModelControllerClient client) throws IOException {
        ModelNode composite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get(STEPS).setEmptyList();

        ModelNode stopServerOne = Util.createOperation(STOP, PathAddress.pathAddress(mainOne));
        stopServerOne.get(BLOCKING).set(true);
        steps.add(stopServerOne);

        ModelNode removeServerOne = Util.createRemoveOperation(PathAddress.pathAddress(mainOne));
        steps.add(removeServerOne);
        validateResponse(client.execute(composite));
    }
}
