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

package org.jboss.as.test.integration.domain.secondaryreconnect;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.List;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.Authentication;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Wrapper for several tests which require a secondary to primary reconnect, so that we need as few reconnects as possible.
 * It makes some assumptions about the HC/server process states,
 * so don't run this within a suite.
 *
 * @author Kabir Khan
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore("[WFCORE-6157] This needs to be investigated - regression of this test was introduced with WFCORE-6151 fix")
public class SecondaryReconnectTestCase {

    static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);
    private static final String RIGHT_PASSWORD = DomainLifecycleUtil.SECONDARY_HOST_PASSWORD;


    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.create(
                DomainTestSupport.Configuration.create(SecondaryReconnectTestCase.class.getSimpleName(),
                        "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary.xml"));

        WildFlyManagedConfiguration primaryConfig = testSupport.getDomainPrimaryConfiguration();
        CallbackHandler callbackHandler = Authentication.getCallbackHandler("secondary", RIGHT_PASSWORD, "ManagementRealm");
        primaryConfig.setCallbackHandler(callbackHandler);

        WildFlyManagedConfiguration secondaryConfig = testSupport.getDomainSecondaryConfiguration();
        secondaryConfig.setCallbackHandler(callbackHandler);

        testSupport.start();

        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
    }

    @Test
    public void test01_OrderedExtensionsAndDeployments() throws Exception {
        testReconnect(new ReconnectTestScenario[]{
                new UnaffectedScenario(650),
                new OrderedChildResourceScenario(),
                new DeploymentScenario(750)
        });
    }

    @Test
    @Ignore("[WFCORE-5549] Unable to remove JBOSS_LOCAL_USER with Elytron configuration.")
    public void test02_RBAC_user_and_model_out_of_sync() throws Exception {
        testReconnect(new ReconnectTestScenario[]{
                new RBACModelOutOfSyncScenario()
        });
    }

    @Test
    public void test03_DeploymentOverlays() throws Exception {
        //Since deployment-overlays affect all servers (https://issues.jboss.org/browse/WFCORE-710), this needs to
        //be tested separately, and to come last since the server state gets affected
        testReconnect(new ReconnectTestScenario[]{
                new UnaffectedScenario(650),
                new DeploymentOverlayScenario(750)
        });
    }

    private void testReconnect(ReconnectTestScenario[] scenarios) throws Exception {
        Throwable t = null;
        int initialisedScenarios = -1;
        try {
            DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
            DomainClient secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
            for (int i = 0; i < scenarios.length; i++) {
                initialisedScenarios = i;
                scenarios[i].setUpDomain(testSupport, primaryClient, secondaryClient);
            }
            //server could have been reloaded in the setUpDomain, get the new clients
            primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
            secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();

            for (ReconnectTestScenario scenario : scenarios) {
                scenario.testOnInitialStartup(primaryClient, secondaryClient);
            }

            //Restart the DC as admin-only
            domainPrimaryLifecycleUtil.reloadAdminOnly("primary");
            primaryClient = domainPrimaryLifecycleUtil.createDomainClient();

            for (ReconnectTestScenario scenario : scenarios) {
                scenario.testWhilePrimaryInAdminOnly(primaryClient, secondaryClient);
            }

            //Restart the DC as normal
            domainPrimaryLifecycleUtil.reload("primary", null, false);
            primaryClient = domainPrimaryLifecycleUtil.createDomainClient();

            //Wait for the secondary to reconnect, look for the secondary in the list of hosts
            long end = System.currentTimeMillis() + 20 * ADJUSTED_SECOND;
            boolean secondaryReconnected = false;
            do {
                Thread.sleep(1 * ADJUSTED_SECOND);
                secondaryReconnected = checkSecondaryReconnected(primaryClient);
            } while (!secondaryReconnected && System.currentTimeMillis() < end);

            //Wait for primary servers to come up
            end = System.currentTimeMillis() + 60 * ADJUSTED_SECOND;
            boolean serversUp = false;
            do {
                Thread.sleep(1 * ADJUSTED_SECOND);
                serversUp = checkHostServersStarted(primaryClient, "primary");
            } while (!serversUp && System.currentTimeMillis() < end);

            for (ReconnectTestScenario scenario : scenarios) {
                scenario.testAfterReconnect(primaryClient, secondaryClient);
            }
        } catch (Throwable thrown) {
            t = thrown;
            if (thrown instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        finally {
            for (int i = initialisedScenarios; i >=0 ; i--) {
                try {
                    scenarios[i].tearDownDomain(
                            testSupport, domainPrimaryLifecycleUtil.getDomainClient(), domainSecondaryLifecycleUtil.getDomainClient());
                } catch (Throwable thrown) {
                    if (t == null) {
                        t = thrown;
                    } else {
                        System.out.println("Caught second failure during cleanup following initial '" + t.toString() + "' failure. Second failure:");
                        thrown.printStackTrace(System.out);
                    }
                    if (thrown instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (t != null) {
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            throw (Error) t;
        }
    }

    private boolean checkSecondaryReconnected(DomainClient primaryClient) throws Exception {
        ModelNode op = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(CHILD_TYPE).set(HOST);
        try {
            ModelNode ret = DomainTestUtils.executeForResult(op, primaryClient);
            List<ModelNode> list = ret.asList();
            if (list.size() == 2) {
                for (ModelNode entry : list) {
                    if ("secondary".equals(entry.asString())){
                        return true;
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean checkHostServersStarted(DomainClient primaryClient, String host) {
        try {
            ModelNode op = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.pathAddress(HOST, host));
            op.get(CHILD_TYPE).set(SERVER);
            ModelNode ret = DomainTestUtils.executeForResult(op, primaryClient);
            List<ModelNode> list = ret.asList();
            for (ModelNode entry : list) {
                String server = entry.asString();
                op = Util.createEmptyOperation(READ_ATTRIBUTE_OPERATION, PathAddress.pathAddress(HOST, host).append(SERVER, server));
                op.get(NAME).set("server-state");
                ModelNode state = DomainTestUtils.executeForResult(op, primaryClient);
                return "running".equals(state.asString());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    static void cloneProfile(DomainClient primaryClient, String source, String target) throws Exception {
        ModelNode clone = Util.createEmptyOperation("clone", PathAddress.pathAddress(PROFILE, source));
        clone.get("to-profile").set(target);
        DomainTestUtils.executeForResult(clone, primaryClient);
    }

    static void createServerGroup(DomainClient primaryClient, String name, String profile) throws Exception {
        ModelNode add = Util.createAddOperation(PathAddress.pathAddress(SERVER_GROUP, name));
        add.get(PROFILE).set(profile);
        add.get(SOCKET_BINDING_GROUP).set("standard-sockets");
        DomainTestUtils.executeForResult(add, primaryClient);
    }

    static void createServer(DomainClient secondaryClient, String name, String serverGroup, int portOffset) throws Exception {
        ModelNode add = Util.createAddOperation(SECONDARY_ADDR.append(SERVER_CONFIG, name));
        add.get(GROUP).set(serverGroup);
        add.get(PORT_OFFSET).set(portOffset);
        DomainTestUtils.executeForResult(add, secondaryClient);
        DomainTestUtils.executeForResult(Util.createAddOperation(SECONDARY_ADDR.append(SERVER_CONFIG, name).append("jvm", "default")), secondaryClient);
    }

    static void startServer(DomainClient secondaryClient, String name) throws Exception {
        ModelNode start = Util.createEmptyOperation(START, SECONDARY_ADDR.append(SERVER_CONFIG, name));
        start.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(start, secondaryClient);
    }

    static void stopServer(DomainClient secondaryClient, String name) throws Exception {
        PathAddress serverAddr = SECONDARY_ADDR.append(SERVER_CONFIG, name);
        ModelNode stop = Util.createEmptyOperation(STOP, serverAddr);
        stop.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(stop, secondaryClient);
    }

    static void removeProfile(DomainClient primaryClient, String name) throws Exception {
        PathAddress profileAddr = PathAddress.pathAddress(PROFILE, name);
        DomainTestUtils.executeForResult(Util.createRemoveOperation(profileAddr.append(SUBSYSTEM, "logging")), primaryClient);
        DomainTestUtils.executeForResult(
                Util.createRemoveOperation(profileAddr), primaryClient);
    }
}
