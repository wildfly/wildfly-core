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

package org.jboss.as.test.integration.domain.suites;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.junit.Assert.assertEquals;

/**
 * Downstream error: if rollout plan is created and master is reloaded, slave is not able to connect to master.
 * (https://issues.jboss.org/browse/WFCORE-3965)
 * Test, which validates this fix, has to be also in upstream, to prevent possible regression.
 *
 * @author <a href="mailto:jondruse@jboss.com">Jiri Ondrusek</a>
 */
public class ReloadWithRolloutPlanTestCase {

    private static final int TIMEOUT = 30000;

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    private static final ModelNode ROLLOUT_PLANS_ADDRESS = new ModelNode().add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
    private static final ModelNode ROLLOUT_PLAN_A = new ModelNode();

    static {
        ROLLOUT_PLANS_ADDRESS.protect();
        ModelNode main = new ModelNode();
        main.get("main-server-group");
        ROLLOUT_PLAN_A.get(ROLLOUT_PLAN, IN_SERIES).add().get("server-group").set(main);
        ROLLOUT_PLAN_A.protect();
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ReloadWithRolloutPlanTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    private DomainClient masterClient;

    @Before
    public void setup() throws Exception {
        masterClient = domainMasterLifecycleUtil.getDomainClient();
    }

    @Test
    public void testReloadMasterWithRolloutPlan() throws IOException {
        final ModelNode addressA = ROLLOUT_PLANS_ADDRESS.clone().add(ROLLOUT_PLAN, "testPlan");

        // Add content
        ModelNode op = Util.getEmptyOperation(ADD, addressA);
        op.get(CONTENT).set(ROLLOUT_PLAN_A);
        ModelNode response = masterClient.execute(op);
        validateResponse(response);
        response = masterClient.execute(getReadAttributeOperation(addressA, CONTENT));
        ModelNode returnVal = validateResponse(response);
        assertEquals(ROLLOUT_PLAN_A, returnVal);

        reloadMaster();

        final ModelNode slave = new ModelNode();
        slave.get(OP).set(READ_ATTRIBUTE_OPERATION);
        slave.get(OP_ADDR).add(HOST, "slave");
        slave.get(NAME).set(HOST_STATE);

        // Wait until slave is reloaded
        boolean slaveIsrunning = validateOutput(slave, masterClient, "running");

        Assert.assertTrue("Cannot validate that host 'slave' is running", slaveIsrunning);

    }

    private static boolean validateOutput(ModelNode operation, ModelControllerClient client, String excpected) {
        final long time = System.currentTimeMillis() + TIMEOUT;
        do {
            try {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final ModelNode result = client.execute(operation);
                if (result.get(OUTCOME).asString().equals(SUCCESS)) {
                    final ModelNode model = result.require(RESULT);
                    if (model.asString().equalsIgnoreCase(excpected)) {
                        return true;
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        } while (System.currentTimeMillis() < time);

        return false;
    }

    private void reloadMaster() throws IOException {
        ModelNode op = new ModelNode();
        op.get(OP).set("reload");
        op.get(OP_ADDR).add(HOST, "master");
        op.get(ADMIN_ONLY).set(false);
        try {
            masterClient.execute(new OperationBuilder(op).build());
        } catch(IOException e) {
            if (!(e.getCause() instanceof ExecutionException)) {
                throw e;
            } // else ignore, this might happen if the channel gets closed before we got the response
        }
    }


    private static ModelNode getReadAttributeOperation(ModelNode address, String attribute) {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).set(address);
        op.get(NAME).set(attribute);
        return op;
    }

}
