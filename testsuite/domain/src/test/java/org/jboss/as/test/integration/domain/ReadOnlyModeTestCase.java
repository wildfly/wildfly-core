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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.impl.DomainClientImpl;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class ReadOnlyModeTestCase {

    private DomainTestSupport.Configuration domainConfig;
    private DomainTestSupport domainManager;
    private DomainLifecycleUtil domainMasterLifecycleUtil;
    private DomainLifecycleUtil domainSlaveLifecycleUtil;
    private static final long TIMEOUT_S = TimeoutUtil.adjust(30);
    private static final int TIMEOUT_SLEEP_MILLIS = 50;
    private static final int FAILED_RELOAD_TIMEOUT_MILLIS = 10000;

    @Before
    public void setupDomain() throws Exception {
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

    @After
    public void tearDownDomain() throws Exception {
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
            op.get(ModelDescriptionConstants.BLOCKING).set(false);
            domainMasterLifecycleUtil.executeAwaitConnectionClosed(op);
            // Try to reconnect to the hc
            domainMasterLifecycleUtil.connect();
            domainMasterLifecycleUtil.awaitHostController(System.currentTimeMillis());

            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asBoolean());
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(masterAddress, "value"))).asBoolean());
            awaitHostControllerRegistration(domainMasterLifecycleUtil.getDomainClient(), "slave");
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
            awaitHostControllerRegistration(domainMasterLifecycleUtil.getDomainClient(), "slave");
            Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(slaveAddress, "value"))).asBoolean());
        }
        domainManager.stop();
        domainConfig.getMasterConfiguration().setRewriteConfigFiles(false);
        domainConfig.getSlaveConfiguration().setRewriteConfigFiles(false);
        domainMasterLifecycleUtil = domainManager.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = domainManager.getDomainSlaveLifecycleUtil();
        domainMasterLifecycleUtil.startAsync();
        domainSlaveLifecycleUtil.startAsync();
        try (final DomainClient clientMaster = getDomainClient(domainConfig.getMasterConfiguration())) {
            waitForHostControllerBeingStarted(TIMEOUT_S, clientMaster);
            Assert.assertTrue(Operations.getFailureDescription(clientMaster.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asString().contains("WFLYCTL0216"));
            Assert.assertTrue(Operations.getFailureDescription(clientMaster.execute(Operations.createReadAttributeOperation(masterAddress, "value"))).asString().contains("WFLYCTL0216"));
            awaitHostControllerRegistration(domainMasterLifecycleUtil.getDomainClient(), "slave");
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

    private boolean awaitHostControllerRegistration(final ModelControllerClient client, final String host) throws Exception {
        final long time = System.currentTimeMillis() + FAILED_RELOAD_TIMEOUT_MILLIS;
        do {
            Thread.sleep(100);
            if (lookupHostInModel(client, host)) {
                return true;
            }
        } while (System.currentTimeMillis() < time);
        return false;
    }

    // mechanism to wait for the the slave to register with the master HC so it is present in the model
    // before continuing.
    private boolean lookupHostInModel(final ModelControllerClient client, final String host) throws Exception {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).add(HOST, host);
        operation.get(NAME).set(HOST_STATE);

        try {
            final ModelNode result = client.execute(operation);
            if (result.get(OUTCOME).asString().equals(SUCCESS)){
                final ModelNode model = result.require(RESULT);
                if (model.asString().equalsIgnoreCase("running")) {
                    return true;
                }
            }
        } catch (IOException e) {
            //
        }
        return false;
    }
}
