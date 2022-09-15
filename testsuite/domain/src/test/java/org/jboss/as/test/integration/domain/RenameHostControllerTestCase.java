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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies we are able to rename a Host Controller and, after reloading it, it gets registered in the domain with the new name.
 *
 * @author Yeray Borges
 */
public class RenameHostControllerTestCase {
    private static final String RENAMED_SECONDARY = "renamed-secondary";
    private static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");
    private static final PathAddress PRIMARY_ADDR = PathAddress.pathAddress(HOST, "primary");
    private static final PathAddress RENAMED_SECONDARY_ADDR = PathAddress.pathAddress(HOST, RENAMED_SECONDARY);

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() {
        testSupport = DomainTestSuite.createSupport(RenameHostControllerTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();

    }

    @AfterClass
    public static void tearDownDomain() {
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void renameSecondary() throws Exception {
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode operation = Util.getWriteAttributeOperation(SECONDARY_ADDR, "name", RENAMED_SECONDARY);
        DomainTestUtils.executeForResult(operation, primaryClient);

        DomainClient secondaryClient = reloadHost(domainSecondaryLifecycleUtil, "secondary");

        String result = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(PathAddress.EMPTY_ADDRESS, "local-host-name"), secondaryClient).asString();

        Assert.assertEquals(RENAMED_SECONDARY, result);

        // verify all is running, it also verifies the secondary is registered in the domain with the new name
        result = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(RENAMED_SECONDARY_ADDR, "host-state"), primaryClient).asString();

        Assert.assertEquals("running", result);

        result = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(PRIMARY_ADDR, "host-state"), primaryClient).asString();

        Assert.assertEquals("running", result);
    }

    private DomainClient reloadHost(DomainLifecycleUtil util, String host) throws Exception {
        ModelNode reload = Util.createEmptyOperation("reload", getRootAddress(host));
        util.executeAwaitConnectionClosed(reload);
        util.connect();
        util.getConfiguration().setHostName(RENAMED_SECONDARY);
        util.awaitHostController(System.currentTimeMillis());
        return util.createDomainClient();
    }

    private PathAddress getRootAddress(String host) {
        return host == null ? PathAddress.EMPTY_ADDRESS : PathAddress.pathAddress(HOST, host);
    }
}
