/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_ENVIRONMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STABILITY;
import static org.jboss.as.server.controller.descriptions.ServerDescriptionConstants.SERVER_ENVIRONMENT;

import java.io.IOException;
import java.util.Arrays;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.wildfly.test.stability.StabilityDomainSetupSnapshotRestoreTasks;

@RunWith(Parameterized.class)
public class DomainStabilityTestCase {
    public PathAddress HOST_PRIMARY = PathAddress.pathAddress(HOST, "primary");
    public PathAddress HOST_SECONDARY = PathAddress.pathAddress(HOST, "secondary");
    public PathAddress SERVER_MAIN_ONE = PathAddress.pathAddress(SERVER, "main-one");
    public PathAddress SERVER_MAIN_THREE = PathAddress.pathAddress(SERVER, "main-three");
    public PathAddress CORE_SERVICE_HOST_ENVIRONMENT = PathAddress.pathAddress(CORE_SERVICE, HOST_ENVIRONMENT);
    public PathAddress CORE_SERVICE_SERVER_ENVIRONMENT = PathAddress.pathAddress(CORE_SERVICE, SERVER_ENVIRONMENT);
    public PathAddress SERVER_GROUP_MAIN_SERVER_GROUP = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");

    private static DomainTestSupport testSupport;
    private final Stability desiredStability;
    private static StabilityDomainSetupSnapshotRestoreTasks setupSnapshotRestoreTasks;

    @Parameterized.Parameters
    public static Iterable<Stability> data() {
        return Arrays.asList(Stability.DEFAULT, Stability.COMMUNITY, Stability.PREVIEW, Stability.EXPERIMENTAL);
    }

    public DomainStabilityTestCase(Stability desiredStability) {
        this.desiredStability = desiredStability;
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.createAndStartDefaultSupport(DomainStabilityTestCase.class.getSimpleName());
    }

    @Before
    public void setup() throws Exception {
        setupSnapshotRestoreTasks = new StabilityDomainSetupSnapshotRestoreTasks(desiredStability, testSupport);
        setupSnapshotRestoreTasks.setup();
    }

    @After
    public void tearDown() throws Exception {
        setupSnapshotRestoreTasks.tearDown();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        if (testSupport != null) {
            testSupport.close();
            testSupport = null;
        }
    }

    @Test
    public void verifyDesiredStability() throws IOException, MgmtOperationException {
        DomainLifecycleUtil domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        DomainLifecycleUtil secondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
        DomainClient secondaryClient = secondaryLifecycleUtil.getDomainClient();

        // Verifies Primary Host Controller
        verifyStability(primaryClient, HOST_PRIMARY.append(CORE_SERVICE_HOST_ENVIRONMENT));
        // Verifies Primary Host Controller managed server
        verifyStability(primaryClient, HOST_PRIMARY.append(SERVER_MAIN_ONE).append(CORE_SERVICE_SERVER_ENVIRONMENT));

        // Verifies Secondary Host Controller
        verifyStability(secondaryClient, HOST_SECONDARY.append(CORE_SERVICE_HOST_ENVIRONMENT));
        // Verifies Secondary Host Controller managed server
        verifyStability(secondaryClient, HOST_SECONDARY.append(SERVER_MAIN_THREE).append(CORE_SERVICE_SERVER_ENVIRONMENT));

        // Verifies stability after other managed server reload operations
        ModelNode params = new ModelNode();
        params.get(BLOCKING).set(true);
        ModelNode op = Util.getOperation("reload-servers", PathAddress.EMPTY_ADDRESS, params);
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.getOperation("reload-servers", SERVER_GROUP_MAIN_SERVER_GROUP, params);
        DomainTestUtils.executeForResult(op, primaryClient);

        // Verifies Primary Host Controller managed server
        verifyStability(primaryClient, HOST_PRIMARY.append(SERVER_MAIN_ONE).append(CORE_SERVICE_SERVER_ENVIRONMENT));
        // Verifies Secondary Host Controller managed server
        verifyStability(secondaryClient, HOST_SECONDARY.append(SERVER_MAIN_THREE).append(CORE_SERVICE_SERVER_ENVIRONMENT));

        op = Util.getOperation("reload", HOST_PRIMARY.append(SERVER_MAIN_ONE), params);
        DomainTestUtils.executeForResult(op, primaryClient);

        // Verifies Primary Host Controller managed server
        verifyStability(primaryClient, HOST_PRIMARY.append(SERVER_MAIN_ONE).append(CORE_SERVICE_SERVER_ENVIRONMENT));
        // Verifies Secondary Host Controller managed server
        verifyStability(secondaryClient, HOST_SECONDARY.append(SERVER_MAIN_THREE).append(CORE_SERVICE_SERVER_ENVIRONMENT));
    }

    private void verifyStability(DomainClient client, PathAddress address) throws IOException, MgmtOperationException {
        ModelNode op = Util.getReadAttributeOperation(address, STABILITY);
        ModelNode result = DomainTestUtils.executeForResult(op, client);
        Stability stability = Stability.fromString(result.asString());
        Assert.assertEquals(desiredStability, stability);
    }
}
