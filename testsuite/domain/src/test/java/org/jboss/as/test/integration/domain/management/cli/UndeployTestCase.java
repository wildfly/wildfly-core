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
package org.jboss.as.test.integration.domain.management.cli;

import java.io.File;
import java.util.Iterator;

import org.jboss.as.cli.CommandContext;

import org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentInfoResult;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.After;
import org.junit.AfterClass;

import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createWarArchive;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ENABLED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkExist;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkMissing;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkEmpty;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.deploymentInfo;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.legacyDeploymentInfo;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class UndeployTestCase extends AbstractCliTestBase {

    private static File cliTestApp1War;

    private static String sgOne;
    private static String sgTwo;

    private CommandContext ctx;
    private static DomainTestSupport testSupport;

    @BeforeClass
    public static void before() throws Exception {
        testSupport = CLITestSuite.createSupport(UndeployTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.masterAddress);

        // deployment1
        cliTestApp1War = createWarArchive("cli-undeploy-test-app1.war", "Version0");

        final Iterator<String> sgI = CLITestSuite.serverGroups.keySet().iterator();
        if (!sgI.hasNext()) {
            fail("Server groups aren't available.");
        }
        sgOne = sgI.next();
        if (!sgI.hasNext()) {
            fail("Second server groups isn't available.");
        }
        sgTwo = sgI.next();
    }

    @AfterClass
    public static void after() throws Exception {
        CLITestSuite.stopSupport();
        AbstractCliTestBase.closeCLI();

        cliTestApp1War.delete();
    }

    @Before
    public void beforeTest() throws Exception {
        ctx = CLITestUtil.getCommandContext(testSupport);
        ctx.connectController();
    }

    @After
    public void afterTest() throws Exception {
        ctx.handleSafe("deployment undeploy * --all-relevant-server-groups");

        ctx.terminateSession();
    }

    /**
     * Test undeploying operation by one application deployment to undeploy all by wildcard in all server group.
     * Uses legacy commands.
     *
     * @throws Exception
     */
    @Test
    public void testLegacyUndeployWithAllServerGroups() throws Exception {
        ctx.handle("deploy --server-groups=" + sgOne + ',' + sgTwo + " " + cliTestApp1War.getAbsolutePath());

        DeploymentInfoResult result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // From serverGroup1 only, still referenced from sg2. Must keep-content
        ctx.handle("undeploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());

        result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        ctx.handle("undeploy --name=* --server-groups=" + sgOne + ',' + sgTwo);

        result = legacyDeploymentInfo(cli, sgOne);
        checkMissing(result, cliTestApp1War.getName());
        checkEmpty(result);
        result = legacyDeploymentInfo(cli, sgTwo);
        checkMissing(result, cliTestApp1War.getName());
        checkEmpty(result);
    }

    /**
     * Test undeploying operation by one application deployment to undeploy all by wildcard in all server group.
     *
     * @throws Exception
     */
    @Test
    public void testUndeployWithAllServerGroups() throws Exception {
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ',' + sgTwo + " " + cliTestApp1War.getAbsolutePath());

        DeploymentInfoResult result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // From serverGroup1 only, still referenced from sg2. Must keep-content
        ctx.handle("deployment undeploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());

        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        ctx.handle("deployment undeploy * --all-relevant-server-groups");

        result = deploymentInfo(cli, sgOne);
        checkMissing(result, cliTestApp1War.getName());
        result = deploymentInfo(cli, sgTwo);
        checkMissing(result, cliTestApp1War.getName());
    }
}
