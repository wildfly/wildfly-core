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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.jboss.as.cli.CommandContext;

import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.deployment.DeploymentInfoUtils;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.After;
import org.junit.AfterClass;

import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createCliArchive;
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createEnterpriseArchive;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ENABLED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.NOT_ADDED;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author jdenise@redhat.com
 */
public class DeployAllDomainTestCase {

    protected static File cliTestApp1War;
    protected static File cliTestApp2War;
    protected static File cliTestAnotherWar;
    protected static File cliTestAppEar;

    protected static String sgOne;
    protected static String sgTwo;

    protected static CommandContext ctx;
    protected static DomainTestSupport testSupport;
    protected static DeploymentInfoUtils infoUtils;

    @BeforeClass
    public static void before() throws Exception {
        testSupport = CLITestSuite.createSupport(UndeployWildcardDomainTestCase.class.getSimpleName());
        infoUtils = new DeploymentInfoUtils(DomainTestSupport.masterAddress);
        infoUtils.connectCli();

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
        infoUtils.disconnectCli();

        cliTestApp1War.delete();
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
        cliTestAppEar.delete();
    }

    @Before
    public void beforeTest() throws Exception {
        ctx = CLITestUtil.getCommandContext(testSupport);
        ctx.connectController();
        infoUtils.enableDoubleCheck(ctx);
    }

    @After
    public void afterTest(){
        ctx.handleSafe("deployment undeploy * --all-relevant-server-groups");
        ctx.terminateSession();
        infoUtils.resetDoubleCheck();
    }

    @Test
    public void testDeploymentLiveCycleWithServerGroups() throws Exception {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        infoUtils.checkDeploymentByList(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName());

        // Step 2b) Verify if applications deployments are enabled for defined server groups by info command
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        // Step 3a) Disabling two selected applications deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail
        try {
            ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp2War.getName());
            fail("Disabling application deployment with wrong server group doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Verification wrong command execution fail - success
        }

        // Step 4) Verify if two selected applications deployments are disabled, but other have still previous state
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        // Step 5) Disable all deployed applications deployments in all server groups
        // TODO Uncomment after fix WFCORE-3562
//        ctx.handle("deployment disable-all --all-relevant-server-groups");

        // TODO remove code after fix WFCORE-3562
//        ctx.handle("deployment disable-all --server-groups=" + sgOne);
//        ctx.handle("deployment disable-all --server-groups=" + sgTwo);
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());
        // #WFCORE-3562

        // Step 6) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);

        // Step 7) Enable one application deployment
        // TODO Uncomment after fix WFCORE-3563
//        ctx.handle("deployment enable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());

        // Step 8) Verify if selected application deployment are enabled, but other have still previous state
//        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);
//
//        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);
        // #WFCORE-3563

        // Step 9) Enable all applications deployments for all server groups
        ctx.handle("deployment enable-all --all-server-groups");

        // Step 10) Verify if all applications deployments are enabled for all server groups
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        // Step 11) Undeploy one application deployment
        ctx.handle("deployment undeploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());
        // Step 12) Check if selected application deployment is removed, but others still exist with right state
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        // Step 13) Undeploy all applications deployments
        ctx.handle("deployment undeploy * --all-relevant-server-groups");
        // Step 14) Check if all applications deployments is gone
        infoUtils.readDeploymentList();
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
    }

    @Test
    public void testDeploymentLegacyLiveCycleWithServerGroups() throws Exception {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        infoUtils.checkDeploymentByList(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName());

        // Step 2b) Verify if applications deployments are enabled for defined server groups by info command
        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        // Step 3a) Disabling two selected applications deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);

        // Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail
        try {
            ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgOne);
            fail("Disabling application deployment with wrong server group doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Verification wrong command execution fail - success
        }

        // Step 4) Verify if two selected applications deployments are disabled, but other have still previous state
        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        // Step 5) Disable all deployed applications deployments in all server groups
        // TODO Uncomment after fix WFCORE-3562
//        ctx.handle("undeploy * --keep-content --all-relevant-server-groups");

        // TODO remove code after fix WFCORE-3562
//        ctx.handle("undeploy * --keep-content --server-groups=" + sgOne);
//        ctx.handle("undeploy * --keep-content --server-groups=" + sgTwo);
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestAnotherWar.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);
        ctx.handle("undeploy " + cliTestAppEar.getName() + " --keep-content --server-groups=" + sgTwo + ',' + sgOne);
        // #WFCORE-3562

        // Step 6) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);

        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);

        // Step 7) Enable one application deployment
        // TODO Uncomment after fix WFCORE-3563
//        ctx.handle("deploy --name=" + cliTestAppEar.getName() + " --server-groups=" + sgTwo + ',' + sgOne);

        // Step 8) Verify if selected application deployment are enabled, but other have still previous state
//        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);
//
//        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
//        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);
        // #WFCORE-3563

        // Step 9) Enable all applications deployments for all server groups
        ctx.handle("deploy --name=* --server-groups=" + sgTwo + ',' + sgOne);

        // Step 10) Verify if all applications deployments are enabled for all server groups
        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        // Step 11) Undeploy one application deployment
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --server-groups=" + sgTwo);
        // Step 12) Check if selected application deployment is removed, but others still exist with right state
        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ENABLED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        // Step 13) Undeploy all applications deployments
        ctx.handle("undeploy * --all-relevant-server-groups");
        // Step 14) Check if all applications deployments is gone
        infoUtils.readDeploymentList();
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
    }

    /**
     * Test for check status of issue WFCORE-3562 by Aesh command
     */
    @Ignore
    @Test
    public void testDisableAllAppDeploymentAllGroup() throws CommandLineException, IOException {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected applications deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3) Disable all deployed applications deployments in all server groups
        ctx.handle("deployment disable-all --all-relevant-server-groups");

        // Step 4) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);
    }

    /**
     * Test for check status of issue WFCORE-3562 by Aesh command
     */
    @Ignore
    @Test
    public void testDisableAllAppDeployment() throws CommandLineException, IOException {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected applications deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3) Disable all deployed applications deployments in all server groups
        ctx.handle("deployment disable-all --server-groups=" + sgOne);
        ctx.handle("deployment disable-all --server-groups=" + sgTwo);

        // Step 4) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);
    }

    /**
     * Test for check status of issue WFCORE-3562 by Aesh command
     */
    @Ignore
    @Test
    public void testEnableSingleAppDeployment() throws CommandLineException, IOException {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected applications deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3) Disable all deployed applications deployments in all server groups
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());

        // Step 4) Enable one application deployment
        ctx.handle("deployment enable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());

        // Step 5) Verify if selected application deployment are enabled, but other have still previous state
        infoUtils.checkDeploymentByInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);
    }

    /**
     * Test for check status of issue WFCORE-3562 by Legacy command
     */
    @Ignore
    @Test
    public void testLegacyDisableAllAppDeploymentAllGroup() throws CommandLineException, IOException {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected applications deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);

        // Step 3) Disable all deployed applications deployments in all server groups
        ctx.handle("undeploy * --keep-content --all-relevant-server-groups");

        // Step 4) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);

        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);
    }

    /**
     * Test for check status of issue WFCORE-3562 by Legacy command
     */
    @Ignore
    @Test
    public void testLegacyDisableAllAppDeployment() throws CommandLineException, IOException {
        // Step 1) Deploy applications deployments to defined server groups
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected applications deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);

        // Step 3) Disable all deployed applications deployments in all server groups
        ctx.handle("undeploy * --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy * --keep-content --server-groups=" + sgTwo);

        // Step 4) Check if all applications deployments is disabled in all server groups
        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);

        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ADDED);
    }

    /**
     * Test for check status of issue WFCORE-3563 by Legacy command
     */
    @Ignore
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
        infoUtils.checkDeploymentByLegacyInfo(sgOne, cliTestApp1War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);

        infoUtils.checkDeploymentByLegacyInfo(sgTwo, cliTestApp1War.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), ADDED);
        infoUtils.checkExistInOutputMemory(cliTestAppEar.getName(), ENABLED);
    }
}
