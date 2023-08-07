/*
 * Copyright 2023 Red Hat, Inc.
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
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
 * Test reload operations against a secondary host executed from the Domain Controller verifying the host controller log file.
 *
 * See https://issues.redhat.com/browse/WFCORE-6423
 */
public class HostReloadProxyTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.createAndStartDefaultSupport(AdminOnlyModeTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        try {
            testSupport.close();
        } finally {
            domainPrimaryLifecycleUtil = null;
            domainSecondaryLifecycleUtil = null;
            testSupport = null;
        }
    }

    @Test
    public void testBasicReloadOperation() throws Exception {
        final Path primaryHostControllerLog = Paths.get(testSupport.getDomainPrimaryConfiguration().getDomainDirectory(), "log", "host-controller.log");

        // Reload the secondary host using the DC client
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode reload = Util.createEmptyOperation("reload",
                PathAddress.pathAddress(ModelDescriptionConstants.HOST, "secondary"));
        reload.get(BLOCKING).set("true");
        DomainTestUtils.executeForResult(reload, primaryClient);

        // Wait until the secondary host has been reloaded
        domainSecondaryLifecycleUtil.awaitHostController(System.currentTimeMillis());

        Assert.assertTrue("WFLYCTL0016 error has been found in the primary controller log file " + primaryHostControllerLog,
                Files.readAllLines(primaryHostControllerLog).stream().noneMatch(l -> l.contains("WFLYCTL0016")));
    }

    @Test
    public void testReloadOperationAdminOnly() throws Exception {
        final Path primaryHostControllerLog = Paths.get(testSupport.getDomainPrimaryConfiguration().getDomainDirectory(), "log", "host-controller.log");

        // Reload the secondary host using the DC client
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        try {
            ModelNode reload = Util.createEmptyOperation("reload",
                    PathAddress.pathAddress(ModelDescriptionConstants.HOST, "secondary"));
            reload.get(ADMIN_ONLY).set(true);
            DomainTestUtils.executeForResult(reload, primaryClient);

            // Wait until the secondary host has been reloaded
            domainSecondaryLifecycleUtil.awaitHostController(System.currentTimeMillis());

            Assert.assertTrue("WFLYCTL0016 error has been found in the primary controller log file " + primaryHostControllerLog,
                    Files.readAllLines(primaryHostControllerLog).stream().noneMatch(l -> l.contains("WFLYCTL0016")));
        } finally {
            domainSecondaryLifecycleUtil.reload("secondary");
        }
    }

    @Test
    public void testBasicReloadRestartingServers() throws Exception {
        final Path primaryHostControllerLog = Paths.get(testSupport.getDomainPrimaryConfiguration().getDomainDirectory(), "log",
                "host-controller.log");

        // Reload the secondary host using the DC client
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        ModelNode reload = Util.createEmptyOperation("reload",
                PathAddress.pathAddress(ModelDescriptionConstants.HOST, "secondary"));
        reload.get(RESTART_SERVERS).set("true");
        DomainTestUtils.executeForResult(reload, primaryClient);

        // Wait until the secondary host has been reloaded
        domainSecondaryLifecycleUtil.awaitHostController(System.currentTimeMillis());

        Assert.assertTrue("WFLYCTL0016 error has been found in the primary controller log file " + primaryHostControllerLog,
                Files.readAllLines(primaryHostControllerLog).stream().noneMatch(l -> l.contains("WFLYCTL0016")));
    }
}
