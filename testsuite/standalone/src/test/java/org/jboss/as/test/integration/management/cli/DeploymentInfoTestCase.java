/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author baranowb
 *
 */
@RunWith(WildflyTestRunner.class)
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
