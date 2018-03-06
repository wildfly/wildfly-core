/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;

import org.jboss.as.cli.CommandContext;

import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentInfoResult;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.After;
import org.junit.AfterClass;

import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createCliArchive;
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createEnterpriseArchive;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ENABLED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.NOT_ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkExist;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkMissing;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkEmpty;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.deploymentInfo;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.deploymentList;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.legacyDeploymentInfo;
import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author jdenise@redhat.com
 */
public class DeployAllDomainTestCase extends AbstractCliTestBase {

    protected static File cliTestApp1War;
    protected static File cliTestApp2War;
    protected static File cliTestAnotherWar;
    protected static File cliTestAppEar;

    protected static String sgOne;
    protected static String sgTwo;

    protected static CommandContext ctx;
    protected static DomainTestSupport testSupport;

    @BeforeClass
    public static void before() throws Exception {
        testSupport = CLITestSuite.createSupport(UndeployWildcardDomainTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.masterAddress);

        // deployment1
        cliTestApp1War = createCliArchive("cli-test-app1-deploy-all.war", "Version0");

        // deployment2
        cliTestApp2War = createCliArchive("cli-test-app2-deploy-all.war", "Version1");

        // deployment3
        cliTestAnotherWar = createCliArchive("cli-test-another-deploy-all.war", "Version2");

        // deployment4
        cliTestAppEar = createEnterpriseArchive();

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
        ctx.handleSafe("deployment undeploy * --all-relevant-server-groups");

        CLITestSuite.stopSupport();
        AbstractCliTestBase.closeCLI();

        cliTestApp1War.delete();
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
        cliTestAppEar.delete();
    }

    @Before
    public void beforeTest() throws Exception {
        ctx = CLITestUtil.getCommandContext(testSupport);
        ctx.connectController();
    }

    @After
    public void afterTest() {
        ctx.handleSafe("deployment undeploy * --all-relevant-server-groups");
        ctx.terminateSession();
    }

    /**
     * Test verify a life cycle of deployment operation in singleton mode with Aesh commands.
     * <ul>
     * <li>Step 1) Deploy applications deployments to defined server groups</li>
     * <li>Step 2a) Verify if deployment are successful by list command</li>
     * <li>Step 2b) Verify if applications deployments are enabled for defined server groups by info command</li>
     * <li>Step 3a) Disabling two selected applications deployments</li>
     * <li>Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail</li>
     * <li>Step 4) Verify if two selected applications deployments are disabled, but other have still previous state</li>
     * <li>Step 5) Disable all deployed applications deployments in all server groups</li>
     * <li>Step 6) Check if all applications deployments is disabled in all server groups</li>
     * <li>Step 7) Enable all applications deployments for all server groups</li>
     * <li>Step 8) Verify if all applications deployments are enabled for all server groups</li>
     * <li>Step 9) Undeploy one application deployment</li>
     * <li>Step 10) Check if selected application deployment is removed, but others still exist with right state</li>
     * <li>Step 11) Undeploy all applications deployments</li>
     * <li>Step 12) Check if all applications deployments is gone</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testDeploymentLiveCycleWithServerGroups() throws Exception {
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorOutput));

        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        DeploymentInfoResult result = deploymentList(cli);
        checkExist(result, cliTestApp1War.getName());
        checkExist(result, cliTestAnotherWar.getName());
        checkExist(result, cliTestApp2War.getName());
        checkExist(result, cliTestAppEar.getName());

        // Step 2b) Verify if applications deployments are enabled for defined server groups by info command
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ENABLED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 3a) Disabling two selected applications deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3b) Try disabling application deployment in wrong server group space
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp2War.getName());
        Assert.assertEquals(String.format("Deployment '%s' is " +
                "already disabled in '%s'." + System.lineSeparator(), cliTestApp2War.getName(), sgOne), errorOutput.toString());

        // Step 4) Verify if two selected applications deployments are disabled, but other have still previous state
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ENABLED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 5) Disable all deployed applications deployments in all server groups
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());

        // Step 6) Check if all applications deployments is disabled in all server groups
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);

        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);

        // Step 7) Enable all applications deployments for all server groups
        ctx.handle("deployment enable-all --all-server-groups");

        // Step 8) Verify if all applications deployments are enabled for all server groups
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ENABLED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 9) Undeploy one application deployment
        ctx.handle("deployment undeploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());
        // Step 10) Check if selected application deployment is removed, but others still exist with right state
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ENABLED, ctx);
        checkMissing(result, cliTestApp2War.getName(), ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkMissing(result, cliTestApp2War.getName(), ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 11) Undeploy all applications deployments
        ctx.handle("deployment undeploy * --all-relevant-server-groups");
        // Step 12) Check if all applications deployments is gone
        result = deploymentList(cli);
        checkMissing(result, cliTestApp1War.getName(), ctx);
        checkMissing(result, cliTestAnotherWar.getName(), ctx);
        checkMissing(result, cliTestApp2War.getName(), ctx);
        checkMissing(result, cliTestAppEar.getName(), ctx);
        checkEmpty(result);
    }

    /**
     * Test verify a life cycle of deployment operation in domain mode with Legacy commands.
     * <ul>
     * <li>Step 1) Deploy applications deployments to defined server groups</li>
     * <li>Step 2a) Verify if deployment are successful by list command</li>
     * <li>Step 2b) Verify if applications deployments are enabled for defined server groups by info command</li>
     * <li>Step 3a) Disabling two selected applications deployments</li>
     * <li>Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail</li>
     * <li>Step 4) Verify if two selected applications deployments are disabled, but other have still previous state</li>
     * <li>Step 5) Disable all deployed applications deployments in all server groups</li>
     * <li>Step 6) Check if all applications deployments is disabled in all server groups</li>
     * <li>Step 7) Enable all applications deployments for all server groups</li>
     * <li>Step 8) Verify if all applications deployments are enabled for all server groups</li>
     * <li>Step 9) Undeploy one application deployment</li>
     * <li>Step 10) Check if selected application deployment is removed, but others still exist with right state</li>
     * <li>Step 11) Undeploy all applications deployments</li>
     * <li>Step 12) Check if all applications deployments is gone</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testDeploymentLegacyLiveCycleWithServerGroups() throws Exception {
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorOutput));

        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        DeploymentInfoResult result = deploymentList(cli);
        checkExist(result, cliTestApp1War.getName());
        checkExist(result, cliTestAnotherWar.getName());
        checkExist(result, cliTestApp2War.getName());
        checkExist(result, cliTestAppEar.getName());

        // Step 2b) Verify if applications deployments are enabled for defined server groups by info command
        result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ENABLED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 3a) Disabling two selected applications deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);

        // Step 3b) Try disabling application deployment in wrong server group space
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgOne);
        Assert.assertEquals(String.format("Deployment '%s' is " +
                "already disabled in '%s'." + System.lineSeparator(), cliTestApp2War.getName(), sgOne), errorOutput.toString());

        // Step 4) Verify if two selected applications deployments are disabled, but other have still previous state
        result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ENABLED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 5) Disable all deployed applications deployments in all server groups
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestAnotherWar.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);
        ctx.handle("undeploy " + cliTestAppEar.getName() + " --keep-content --server-groups=" + sgTwo + ',' + sgOne);

        // Step 6) Check if all applications deployments is disabled in all server groups
        result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);

        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);

        // Step 7) Enable all applications deployments for all server groups
        ctx.handle("deploy --name=* --server-groups=" + sgTwo + ',' + sgOne);

        // Step 8) Verify if all applications deployments are enabled for all server groups
        result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ENABLED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 9) Undeploy one application deployment
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --server-groups=" + sgTwo);
        // Step 10) Check if selected application deployment is removed, but others still exist with right state
        result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ENABLED, ctx);
        checkMissing(result, cliTestApp2War.getName(), ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkMissing(result, cliTestApp2War.getName(), ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 11) Undeploy all applications deployments
        ctx.handle("undeploy * --all-relevant-server-groups");
        // Step 12) Check if all applications deployments is gone
        result = deploymentList(cli);
        checkMissing(result, cliTestApp1War.getName());
        checkMissing(result, cliTestAnotherWar.getName());
        checkMissing(result, cliTestApp2War.getName());
        checkMissing(result, cliTestAppEar.getName());
        checkEmpty(result);
    }

    /**
     * Separate test for check status of issue WFCORE-3563 by Legacy command.
     * Concentration is for enabling one application deployments with separates group by Legacy command.
     */
    @Test
    public void testLegacyEnableSingleAppDeployment() throws CommandLineException, IOException {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected applications deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);

        // Step 3) Disable all deployed applications deployments in all server groups
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestAnotherWar.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);
        ctx.handle("undeploy " + cliTestAppEar.getName() + " --keep-content --server-groups=" + sgTwo + ',' + sgOne);

        // Step 4) Enable one application deployment
        ctx.handle("deploy --name=" + cliTestAppEar.getName() + " --server-groups=" + sgTwo + ',' + sgOne);

        // Step 5) Verify if selected application deployment are enabled, but other have still previous state
        DeploymentInfoResult result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);
    }
}
