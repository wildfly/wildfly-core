/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain;

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
    private DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @Before
    public void setupDomain() throws Exception {
        DomainTestSupport.Configuration domainConfig = DomainTestSupport.Configuration.create(ReadOnlyModeTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary.xml");
        domainConfig.getPrimaryConfiguration().setReadOnlyHost(true);
        domainConfig.getPrimaryConfiguration().setReadOnlyDomain(true);
        domainConfig.getSecondaryConfiguration().setReadOnlyDomain(true);
        domainConfig.getSecondaryConfiguration().setReadOnlyHost(false);
        domainManager = DomainTestSupport.create(domainConfig);
        domainManager.start();
        domainPrimaryLifecycleUtil = domainManager.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = domainManager.getDomainSecondaryLifecycleUtil();
    }

    @After
    public void tearDownDomain() throws Exception {
        domainManager.close();
        domainManager = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
    }

    @Test
    public void testConfigurationNotUpdated() throws Exception {
        ModelNode domainAddress = PathAddress.pathAddress("system-property", "domain-read-only").toModelNode();
        ModelNode primaryAddress = PathAddress.pathAddress("host", "primary").append("system-property", "primary-read-only").toModelNode();
        ModelNode secondaryAddress = PathAddress.pathAddress("host", "secondary").append("system-property", "secondary-read-only").toModelNode();
        DomainClient primaryClient = domainPrimaryLifecycleUtil.getDomainClient();

        ModelNode op = Operations.createAddOperation(domainAddress);
        op.get("value").set(true);
        Operations.isSuccessfulOutcome(primaryClient.execute(op));
        op = Operations.createAddOperation(primaryAddress);
        op.get("value").set(true);
        Operations.isSuccessfulOutcome(primaryClient.execute(op));
        op = Operations.createAddOperation(secondaryAddress);
        op.get("value").set(true);
        Operations.isSuccessfulOutcome(primaryClient.execute(op));
        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asBoolean());
        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(primaryAddress, "value"))).asBoolean());
        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(secondaryAddress, "value"))).asBoolean());

        // reload primary HC
        domainPrimaryLifecycleUtil.reload("primary");

        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asBoolean());
        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(primaryAddress, "value"))).asBoolean());
        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(secondaryAddress, "value"))).asBoolean());

        // reload secondary HC
        domainSecondaryLifecycleUtil.reload("secondary");

        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asBoolean());
        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(primaryAddress, "value"))).asBoolean());
        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(secondaryAddress, "value"))).asBoolean());

        domainSecondaryLifecycleUtil.stop();
        domainPrimaryLifecycleUtil.stop();

        domainPrimaryLifecycleUtil.getConfiguration().setRewriteConfigFiles(false);
        domainSecondaryLifecycleUtil.getConfiguration().setRewriteConfigFiles(false);
        Future<Void> primaryFuture = domainPrimaryLifecycleUtil.startAsync();
        Future<Void> secondaryFuture = domainSecondaryLifecycleUtil.startAsync();
        primaryFuture.get();
        secondaryFuture.get();

        primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        Assert.assertTrue(Operations.getFailureDescription(primaryClient.execute(Operations.createReadAttributeOperation(domainAddress, "value"))).asString().contains("WFLYCTL0216"));
        Assert.assertTrue(Operations.getFailureDescription(primaryClient.execute(Operations.createReadAttributeOperation(primaryAddress, "value"))).asString().contains("WFLYCTL0216"));
        Assert.assertTrue(Operations.readResult(primaryClient.execute(Operations.createReadAttributeOperation(secondaryAddress, "value"))).asBoolean());

    }

}
