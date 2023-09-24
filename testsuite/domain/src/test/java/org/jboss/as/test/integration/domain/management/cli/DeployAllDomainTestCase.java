/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management.cli;

import java.io.File;
import java.io.IOException;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
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
        AbstractCliTestBase.initCLI(DomainTestSupport.primaryAddress);

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
        ctx.handleSafe("deployment undeploy * --all-relevant-server-groups");
    }

    @After
    public void afterTest() {
        ctx.handleSafe("deployment undeploy * --all-relevant-server-groups");
        ctx.terminateSession();
    }

    /**
     * Test verify a life cycle of deployment operation in singleton mode with Aesh commands.
     * <ul>
     * <li>Step 1) Deploy deployments to defined server groups</li>
     * <li>Step 2a) Verify if deployment are successful by list command</li>
     * <li>Step 2b) Verify if deployments are enabled for defined server groups by info command</li>
     * <li>Step 3a) Disabling two selected deployments</li>
     * <li>Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail</li>
     * <li>Step 4) Verify if two selected deployments are disabled, but other have still previous state</li>
     * <li>Step 5) Disable all deployed deployments in all server groups</li>
     * <li>Step 6) Check if all deployments are disabled in all server groups</li>
     * <li>Step 7) Enable all deployments for all server groups</li>
     * <li>Step 8) Verify if all deployments are enabled for all server groups</li>
     * <li>Step 9) Undeploy one application deployment</li>
     * <li>Step 10) Check if selected application deployment is removed, but others still exist with right state</li>
     * <li>Step 11) Undeploy all deployments</li>
     * <li>Step 12) Check if all deployments is gone</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testDeploymentLiveCycleWithServerGroups() throws Exception {
        // Step 1) Deploy deployments to defined server groups
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

        // Step 2b) Verify if deployments are enabled for defined server groups by info command
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

        // Step 3a) Disabling two selected deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail
        try {
            ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp2War.getName());
            fail("Disabling application deployment with wrong server group doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!",
                    ex.getMessage(),
                    allOf(containsString("WFLYCTL0216"),
                            containsString(sgOne),
                            containsString(cliTestApp2War.getName())));
            // Verification wrong command execution fail - success
        }

        // Step 4) Verify if two selected deployments are disabled, but other have still previous state
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

        // Step 5) Disable all deployed deployments in all server groups
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());

        // Step 6) Check if all deployments are disabled in all server groups
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

        // Step 7) Enable all deployments for all server groups
        ctx.handle("deployment enable-all --all-server-groups");

        // Step 8) Verify if all deployments are enabled for all server groups
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

        // Step 11) Undeploy all deployments
        ctx.handle("deployment undeploy * --all-relevant-server-groups");
        // Step 12) Check if all deployments is gone
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
     * <li>Step 1) Deploy deployments to defined server groups</li>
     * <li>Step 2a) Verify if deployment are successful by list command</li>
     * <li>Step 2b) Verify if deployments are enabled for defined server groups by info command</li>
     * <li>Step 3a) Disabling two selected deployments</li>
     * <li>Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail</li>
     * <li>Step 4) Verify if two selected deployments are disabled, but other have still previous state</li>
     * <li>Step 5) Disable all deployed deployments in all server groups</li>
     * <li>Step 6) Check if all deployments are disabled in all server groups</li>
     * <li>Step 7) Enable all deployments for all server groups</li>
     * <li>Step 8) Verify if all deployments are enabled for all server groups</li>
     * <li>Step 9) Undeploy one application deployment</li>
     * <li>Step 10) Check if selected application deployment is removed, but others still exist with right state</li>
     * <li>Step 11) Undeploy all deployments</li>
     * <li>Step 12) Check if all deployments is gone</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testDeploymentLegacyLiveCycleWithServerGroups() throws Exception {
        // Step 1) Deploy deployments to defined server groups
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

        // Step 2b) Verify if deployments are enabled for defined server groups by info command
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

        // Step 3a) Disabling two selected deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);

        // Step 3b) Try disabling application deployment in wrong server group space. expect command execution fail
        try {
            ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgOne);
            fail("Disabling application deployment with wrong server group doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!",
                    ex.getMessage(),
                    allOf(containsString("WFLYCTL0216:"),
                            containsString(sgOne),
                            containsString(cliTestApp2War.getName())));
            // Verification wrong command execution fail - success
        }

        // Step 4) Verify if two selected deployments are disabled, but other have still previous state
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

        // Step 5) Disable all deployed deployments in all server groups
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestAnotherWar.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);
        ctx.handle("undeploy " + cliTestAppEar.getName() + " --keep-content --server-groups=" + sgTwo + ',' + sgOne);

        // Step 6) Check if all deployments are disabled in all server groups
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

        // Step 7) Enable all deployments for all server groups
        ctx.handle("deploy --name=* --server-groups=" + sgTwo + ',' + sgOne);

        // Step 8) Verify if all deployments are enabled for all server groups
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

        // Step 11) Undeploy all deployments
        ctx.handle("undeploy * --all-relevant-server-groups");
        // Step 12) Check if all deployments is gone
        result = deploymentList(cli);
        checkMissing(result, cliTestApp1War.getName());
        checkMissing(result, cliTestAnotherWar.getName());
        checkMissing(result, cliTestApp2War.getName());
        checkMissing(result, cliTestAppEar.getName());
        checkEmpty(result);
    }

    /**
     * Enabling one application deployment.
     * Using backward compatibility commands.
     *
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3563">WFCORE-3563</a>
     */
    @Test
    public void testLegacyEnableSingleAppDeployment() throws CommandLineException, IOException {
        // Step 1) Deploy deployments to defined server groups
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);

        // Step 3) Disable all deployed deployments in all server groups
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

    /**
     * Disabling all deployed application deployments present in all server groups.
     *
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3562">WFCORE-3562</a>
     */
    @Test
    public void testDisableAllAppDeploymentAllGroup() throws CommandLineException, IOException {
        // Step 1) Deploy deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());


        // Step 2) Disabling two selected deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3) Disable all deployed deployments in all server groups
        ctx.handle("deployment disable-all --all-relevant-server-groups");

        // Step 4) Check if all deployments are disabled in all server groups
        DeploymentInfoResult result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);

        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);
    }

    /**
     * Disabling all deployments of one server group that are also present in a second server group.
     *
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3562">WFCORE-3562</a>
     */
    @Test
    public void testDisableAllAppDeployment() throws CommandLineException, IOException {
        // Step 1) Deploy deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3) Disable all deployed deployments in first server groups
        ctx.handle("deployment disable-all --server-groups=" + sgOne);

        // Step 4) Check if all deployments state doesn't change
        DeploymentInfoResult result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);

        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 5) Disable all deployed deployments in seconds server group
        ctx.handle("deployment disable-all --server-groups=" + sgTwo);

        // Step 6) Check if all remaining enabled deployments from the second group are disabled
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
    }

    /**
     * Enabling one application deployment.
     *
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3562">WFCORE-3562</a>
     */
    @Test
    public void testEnableSingleAppDeployment() throws CommandLineException, IOException {
        // Step 1) Deploy deployments to defined server groups
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected deployments
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());

        // Step 3) Disable all deployed deployments in all server groups
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        ctx.handle("deployment disable --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ' ' + cliTestApp2War.getName());
        ctx.handle("deployment disable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());

        // Step 4) Enable one application deployment
        ctx.handle("deployment enable --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getName());

        // Step 5) Verify if selected application deployment are enabled, but other have still previous state
        DeploymentInfoResult result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);
    }


    /**
     * Disabling all deployed application deployments present in all server groups.
     * Using backward compatibility commands.
     *
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3562">WFCORE-3562</a>
     */
    @Test
    public void testLegacyDisableAllAppDeploymentAllGroup() throws CommandLineException, IOException {
        // Step 1) Deploy deployments to defined server groups
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);

        // Step 3) Disable all deployed deployments in all server groups
        ctx.handle("undeploy * --keep-content --all-relevant-server-groups");

        // Step 4) Check if all deployments are disabled in all server groups
        DeploymentInfoResult result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);

        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);
    }

    /**
     * Disabling all deployments of one server group that are also present in a second server group.
     * Using backward compatibility commands.
     *
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3562">WFCORE-3562</a>
     */
    @Test
    public void testLegacyDisableAllAppDeployment() throws CommandLineException, IOException {
        // Step 1) Deploy deployments to defined server groups
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Step 2) Disabling two selected deployments
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);
        ctx.handle("undeploy " + cliTestApp2War.getName() + " --keep-content --server-groups=" + sgTwo);


        // Step 3) Disable all deployed deployments in first server groups
        ctx.handle("undeploy * --keep-content --server-groups=" + sgOne);

        // Step 4) Check if deployments in first server group are disabled but in second server group doesn't
        DeploymentInfoResult result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ADDED, ctx);

        result = legacyDeploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), NOT_ADDED, ctx);
        checkExist(result, cliTestApp2War.getName(), ADDED, ctx);
        checkExist(result, cliTestAppEar.getName(), ENABLED, ctx);

        // Step 5) Disable all deployed deployments in second server groups
        ctx.handle("undeploy * --keep-content --server-groups=" + sgTwo);

        // Step 6) Check if all remaining enabled deployments from the second group are disabled
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
    }
}
