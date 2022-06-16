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
import java.util.concurrent.Future;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
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

    private DomainTestSupport domainManager;
    private DomainLifecycleUtil domainMasterLifecycleUtil;
    private DomainLifecycleUtil domainSlaveLifecycleUtil;

    @Before
    public void setupDomain() throws Exception {
        DomainTestSupport.Configuration domainConfig = DomainTestSupport.Configuration.create(ReadOnlyModeTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary.xml");
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
        domainManager.close();
        domainManager = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
    }

    @Test
    public void testConfigurationNotUpdated() throws Exception {
        ModelNode domainAddress = PathAddress.pathAddress("system-property", "domain-read-only").toModelNode();
        ModelNode masterAddress = PathAddress.pathAddress("host", "primary").append("system-property", "master-read-only").toModelNode();
        ModelNode slaveAddress = PathAddress.pathAddress("host", "secondary").append("system-property", "slave-read-only").toModelNode();
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();

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
        op.get(OP_ADDR).add(HOST, "primary");
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
        op.get(OP_ADDR).add(HOST, "secondary");
        op.get(OP).set("reload");
        domainSlaveLifecycleUtil.executeAwaitConnectionClosed(op);
        // Try to reconnect to the hc
        domainSlaveLifecycleUtil.connect();
        domainSlaveLifecycleUtil.awaitHostController(System.currentTimeMillis());

        Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asBoolean());
        Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(masterAddress, "value"))).asBoolean());
        Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(slaveAddress, "value"))).asBoolean());

        domainSlaveLifecycleUtil.stop();
        domainMasterLifecycleUtil.stop();

        domainMasterLifecycleUtil.getConfiguration().setRewriteConfigFiles(false);
        domainSlaveLifecycleUtil.getConfiguration().setRewriteConfigFiles(false);
        Future<Void> masterFuture = domainMasterLifecycleUtil.startAsync();
        Future<Void> slaveFuture = domainSlaveLifecycleUtil.startAsync();
        masterFuture.get();
        slaveFuture.get();

        masterClient = domainMasterLifecycleUtil.getDomainClient();
        Assert.assertTrue(Operations.getFailureDescription(masterClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asString().contains("WFLYCTL0216"));
        Assert.assertTrue(Operations.getFailureDescription(masterClient.execute(Operations.createReadAttributeOperation(masterAddress, "value"))).asString().contains("WFLYCTL0216"));
        Assert.assertTrue(Operations.readResult(masterClient.execute(Operations.createReadAttributeOperation(slaveAddress, "value"))).asBoolean());

    }

}
