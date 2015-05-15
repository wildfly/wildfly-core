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

package org.jboss.as.test.integration.domain.slavereconnect;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;

import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Wrapper for several tests which require a slave to master reconnect, so that we only need to do
 * one reconnect. It akes some assumptions about the HC/server process states,
 * so don't run this within a suite.
 *
 * @author Kabir Khan
 */
public class SlaveReconnectTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);

    private static final ReconnectTestScenario[] SCENARIOS = new ReconnectTestScenario[]{
            new OrderedChildResourceScenario(),
            new DeploymentScenario(),
    };

    private static int initializedScenarios = 0;
    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(SlaveReconnectTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        DomainClient slaveClient = domainSlaveLifecycleUtil.getDomainClient();

        for (int i = 0; i < SCENARIOS.length; i++) {
            initializedScenarios = i;
            SCENARIOS[i].setUpDomain(testSupport, masterClient, slaveClient);
        }
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        DomainClient slaveClient = domainSlaveLifecycleUtil.getDomainClient();
        for (int i = initializedScenarios ; i >= 0 ; i--) {
            SCENARIOS[i].tearDownDomain(masterClient, slaveClient);
        }
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testOrderedExtension() throws Exception {
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        DomainClient slaveClient = domainSlaveLifecycleUtil.getDomainClient();
        for (ReconnectTestScenario scenario : SCENARIOS) {
            scenario.testOnInitialStartup(masterClient, slaveClient);
        }

        //Restart the DC as admin-only
        ModelNode restartAdminOnly = Util.createEmptyOperation("reload", PathAddress.pathAddress(HOST, "master"));
        restartAdminOnly.get("admin-only").set(true);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(restartAdminOnly);
        domainMasterLifecycleUtil.connect();
        domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());
        masterClient = domainMasterLifecycleUtil.createDomainClient();

        for (ReconnectTestScenario scenario : SCENARIOS) {
            scenario.testWhileMasterInAdminOnly(masterClient, slaveClient);
        }

        //Restart the DC as normal
        restartAdminOnly.get("admin-only").set(false);
        domainMasterLifecycleUtil.executeAwaitConnectionClosed(restartAdminOnly);
        domainMasterLifecycleUtil.connect();
        domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());
        masterClient = domainMasterLifecycleUtil.createDomainClient();

        //Wait for the slave to reconnect, look for the slave in the list of hosts
        long end = System.currentTimeMillis() + 20 * ADJUSTED_SECOND;
        boolean slaveReconnected = false;
        do {
            Thread.sleep(1 * ADJUSTED_SECOND);
            slaveReconnected = checkSlaveReconnected(masterClient);
        } while (!slaveReconnected && System.currentTimeMillis() < end);

        for (ReconnectTestScenario scenario : SCENARIOS) {
            scenario.testAfterReconnect(masterClient, slaveClient);
        }
    }

    private boolean checkSlaveReconnected(DomainClient masterClient) throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(CHILD_TYPE).set(HOST);
        try {
            ModelNode ret = DomainTestUtils.executeForResult(op, masterClient);
            List<ModelNode> list = ret.asList();
            if (list.size() == 2) {
                for (ModelNode entry : list) {
                    if ("slave".equals(entry.asString())){
                        return true;
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
    }
}
