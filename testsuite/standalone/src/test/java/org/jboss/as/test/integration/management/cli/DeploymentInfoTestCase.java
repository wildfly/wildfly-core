/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author baranowb
 *
 */
@RunWith(WildFlyRunner.class)
public class DeploymentInfoTestCase extends AbstractCliTestBase {

    private CommandContext ctx;

    @BeforeClass
    public static void beforeClass() throws Exception {
        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        AbstractCliTestBase.closeCLI();
    }

    @Before
    public void before() throws Exception {
        ctx = CLITestUtil.getCommandContext();
        ctx.connectController();
    }

    @After
    public void after() throws Exception {
        ctx.terminateSession();
    }

    @Test
    public void testBasicPathArgumentRecognitionAndParsing() throws Exception {
        try {
            ctx.buildRequest("deployment-info --headers");
            Assert.fail();
        } catch (CommandFormatException cfe) {

        }

        try {
            ctx.buildRequest("deployment info --headers");
            Assert.fail();
        } catch (CommandFormatException cfe) {

        }
    }

    @Test
    public void testBasicPathArgumentRecognitionAndParsing2() throws Exception {
        try {
            ctx.buildRequest("deployment-info --headers=false");
            Assert.fail();
        } catch (CommandFormatException cfe) {

        }

        try {
            ctx.buildRequest("deployment info --headers=false");
            Assert.fail();
        } catch (CommandFormatException cfe) {

        }
    }

    @Test
    public void testBasicPathArgumentRecognitionAndParsing3() throws Exception {
        try {
            ctx.buildRequest("deployment-info --headers=damn");
            Assert.fail();
        } catch (CommandFormatException cfe) {

        }

        try {
            ctx.buildRequest("deployment info --headers=damn");
            Assert.fail();
        } catch (CommandFormatException cfe) {

        }
    }

    @Test
    public void testBasicPathArgumentRecognitionAndParsing4() throws Exception {
        {
            ModelNode request = ctx.buildRequest("deployment-info --headers={}");
            Assert.assertTrue(request.hasDefined(Util.OPERATION_HEADERS));
            ModelNode opHeaders = request.get(Util.OPERATION_HEADERS);
            Assert.assertTrue(opHeaders.asList().size() == 0);
        }
        {
            ModelNode request = ctx.buildRequest("deployment info --headers={}");
            Assert.assertTrue(request.hasDefined(Util.OPERATION_HEADERS));
            ModelNode opHeaders = request.get(Util.OPERATION_HEADERS);
            Assert.assertTrue(opHeaders.asList().size() == 0);
        }
    }
}
