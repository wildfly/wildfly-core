/*
 * Copyright 2018 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.impl.DomainClientImpl;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class ReadOnlyModeTestCase {

    private static DomainTestSupport.Configuration domainConfig;
    private static DomainTestSupport domainManager;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;
    private static final long TIMEOUT_S = TimeoutUtil.adjust(30);
    private static final int TIMEOUT_SLEEP_MILLIS = 50;

    @BeforeClass
    public static void setupDomain() throws Exception {
       domainConfig = DomainTestSupport.Configuration.create(ReadOnlyModeTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-master.xml", "host-configs/host-slave.xml");
        domainConfig.getMasterConfiguration().setReadOnlyHost(true);
        domainConfig.getMasterConfiguration().setReadOnlyDomain(true);
        domainConfig.getSlaveConfiguration().setReadOnlyDomain(true);
        domainConfig.getSlaveConfiguration().setReadOnlyHost(false);
        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.start();
        domainMasterLifecycleUtil = domainManager.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = domainManager.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        domainManager.stop();
        domainManager = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
    }

    @Test
    public void testConfigurationNotUpdated() throws Exception {
        ModelNode domainAddress = PathAddress.pathAddress("system-property", "domain-read-only").toModelNode();
        ModelNode masterAddress = PathAddress.pathAddress("host", "master").append("system-property", "master-read-only").toModelNode();
        ModelNode slaveAddress = PathAddress.pathAddress("host", "slave").append("system-property", "slave-read-only").toModelNode();
        try (final DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient()) {
            ModelNode op = Operations.createAddOperation(domainAddress);
            op.get("value").set(true);
            Operations.isSuccessfulOutcome(masterClient.execute(op));
            op = Operations.createAddOperation(masterAddress);
            op.get("value").set(true);
            Operations.isSuccessfulOutcome(masterClient.execute(op));
            op = Operations.createAddOperation(slaveAddress);
            op.get("value").set(true);
            Operations.isSuccessfulOutcome(masterClient.execute(op));
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asBoolean());
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(masterAddress, "value"))).asBoolean());
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(slaveAddress, "value"))).asBoolean());

            // reload master HC
            op = new ModelNode();
            op.get(OP_ADDR).add(HOST, "master");
            op.get(OP).set("reload");
            domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);
            // Try to reconnect to the hc
            domainMasterLifecycleUtil.connect();
            domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());

            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asBoolean());
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(masterAddress, "value"))).asBoolean());
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(slaveAddress, "value"))).asBoolean());

            // reload slave HC
            op = new ModelNode();
            op.get(OP_ADDR).add(HOST, "slave");
            op.get(OP).set("reload");
            domainSlaveLifecycleUtil.executeAwaitConnectionClosed(op);
            // Try to reconnect to the hc
            domainSlaveLifecycleUtil.connect();
            domainSlaveLifecycleUtil.awaitHostController(System.currentTimeMillis());

            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asBoolean());
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(masterAddress, "value"))).asBoolean());
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(slaveAddress, "value"))).asBoolean());
        }
        domainManager.stop();
        domainConfig.getMasterConfiguration().setRewriteConfigFiles(false);
        domainConfig.getSlaveConfiguration().setRewriteConfigFiles(false);
        domainManager.getDomainMasterLifecycleUtil().startAsync();
        domainManager.getDomainSlaveLifecycleUtil().startAsync();
        try (final DomainClient clientMaster = getDomainClient(domainConfig.getMasterConfiguration())) {
            waitForHostControllerBeingStarted(TIMEOUT_S, clientMaster);
            Assert.assertTrue(Operations.getFailureDescription(clientMaster.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asString().contains("WFLYCTL0216"));
            Assert.assertTrue(Operations.getFailureDescription(clientMaster.execute(Operations.createReadAttributeOperation(masterAddress, "value"))).asString().contains("WFLYCTL0216"));
            Assert.assertTrue(Operations.readResult(clientMaster.execute(Operations.createReadAttributeOperation(slaveAddress, "value"))).asBoolean());
        }
    }


    private void waitForHostControllerBeingStarted(long timeoutSeconds, DomainClient client) {
        runWithTimeout(timeoutSeconds, () -> client.getServerStatuses());
    }

    private void runWithTimeout(long timeoutSeconds, Runnable voidFunctionInterface) {
        runWithTimeout(timeoutSeconds, () -> {
            voidFunctionInterface.run();
            return null;
        });
    }

    private <T> T runWithTimeout(long timeoutSeconds, Supplier<T> function) {
        final long timeoutTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while(true) {
            try {
                return function.get();
            } catch (Throwable t) {
                if(timeoutTime < System.currentTimeMillis()) {
                    throw new IllegalStateException("Function '" + function
                        + "' failed to process in " + timeoutSeconds + " s, caused: " + t.getMessage() , t);
                }
                try {
                    Thread.sleep(TIMEOUT_SLEEP_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private DomainClient getDomainClient(WildFlyManagedConfiguration config) throws UnknownHostException {
        final InetAddress address = InetAddress.getByName(config.getHostControllerManagementAddress());
        final int port = config.getHostControllerManagementPort();
        final String protocol = config.getHostControllerManagementProtocol();
        return new DomainClientImpl(protocol, address, port);
    }

}
