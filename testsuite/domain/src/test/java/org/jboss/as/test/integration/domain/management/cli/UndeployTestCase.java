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

import java.io.ByteArrayOutputStream;
import java.io.File;
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

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createWarArchive;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ADDED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ENABLED;
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

public class UndeployTestCase extends AbstractCliTestBase {

    private static final String WRONG_DEPLOYMENT = "testRo.war";

    private static File cliTestApp1War;
    private static File cliTestApp2War;
    private static File cliTestAnotherWar;

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

        // deployment2
        cliTestApp2War = createWarArchive("cli-undeploy-test-app2.war", "Version1");

        // deployment3
        cliTestAnotherWar = createWarArchive("cli-undeploy-test-another.war", "Version2");

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
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
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
        try {
            ctx.handle("undeploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        } catch (CommandLineException ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!",
                    ex.getMessage(),
                    allOf(containsString("Cannot undeploy"),
                            containsString(cliTestApp1War.getName()),
                            containsString(" as it is still deployed in "),
                            containsString(sgTwo),
                            containsString("Please specify all relevant server groups.")));
            // Verification wrong command execution fail - success
        }

        result = legacyDeploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
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
        try {
            ctx.handle("deployment undeploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());
        } catch (CommandLineException ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!",
                    ex.getMessage(),
                    allOf(containsString("Cannot undeploy"),
                            containsString(cliTestApp1War.getName()),
                            containsString(" as it is still deployed in "),
                            containsString(sgTwo),
                            containsString("Please specify all relevant server groups.")));
            // Verification wrong command execution fail - success
        }

        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        ctx.handle("deployment undeploy * --all-relevant-server-groups");

        result = deploymentInfo(cli, sgOne);
        checkMissing(result, cliTestApp1War.getName());
        result = deploymentInfo(cli, sgTwo);
        checkMissing(result, cliTestApp1War.getName());
    }

    /**
     * Testing disabling of non-deployed application deployments.
     * Verify if status of application deployments hasn't change.
     * Verify error message.
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testDisableWrongDeployment() throws Exception {
        // Deploying application deployment for verify failed disable command doesn't have effect to other deployments
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath() + " --server-groups=" + sgOne);
        ctx.handle("deployment deploy-file " + cliTestApp2War.getAbsolutePath() + " --server-groups=" + sgOne);

        // Remember status of application deployment before deploy operation
        final DeploymentInfoResult before = deploymentInfo(cli, sgOne);

        // Try disable non installed application deployment
        try {
            ctx.handle("deployment disable " + WRONG_DEPLOYMENT + " --server-groups=" + sgOne);
            fail("Disable non installed application deployment doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("Deployment '" + WRONG_DEPLOYMENT +
                            "' is not found among the registered deployments."));
            // Verification wrong command execution fail - success
        }
        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli, sgOne);
        if (!after.isOutputEmpty()) {
            assertThat("After disabling of non-deployed application deployment something is change.",
                    after, is(before));
        }
    }

    /**
     * Testing disabling of non-deployed application deployments.
     * Using backward compatibility commands.
     * Verify if status of application deployments hasn't change.
     * Verify error message.
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testLegacyDisableWrongDeployment() throws Exception {
        // Deploying application deployment for verify failed disable command doesn't have effect to other deployments
        ctx.handle("deploy " + cliTestApp1War.getAbsolutePath() + " --server-groups=" + sgOne);
        ctx.handle("deploy " + cliTestApp2War.getAbsolutePath() + " --server-groups=" + sgOne);

        // Remember status of application deployment before deploy operation
        final DeploymentInfoResult before = deploymentInfo(cli, sgOne);

        // Try disable non installed application deployment
        try {
            ctx.handle("undeploy " + WRONG_DEPLOYMENT + " --keep-content --server-groups=" + sgOne);
            fail("Disable non installed application deployment doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("Deployment '" + WRONG_DEPLOYMENT +
                            "' is not found among the registered deployments."));
            // Verification wrong command execution fail - success
        }
        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli, sgOne);
        if (!after.isOutputEmpty()) {
            assertThat("After disabling of non-deployed application deployment something is change.",
                    after, is(before));
        }
    }

    /**
     * Testing undeploy already undeployed application deployments.
     * Verify if status of application deployments hasn't change.
     * Verify error message.
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testUndeployUndeployedDeployment() throws Exception {
        // Step 1) Deploy application deployment
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath() + " --server-groups=" + sgOne);

        // Step 2) Check if applications deployments is installed and in right state
        DeploymentInfoResult result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // Step 3) Undeploy application deployment from server group one
        ctx.handle("deployment undeploy " + cliTestApp1War.getName() + " --server-groups=" + sgOne);

        // Step 4) Check if applications deployments is correctly uninstalled
        result = deploymentInfo(cli, sgOne);
        checkMissing(result, cliTestApp1War.getName(), ctx);

        // Step 5) Try undeploy already undeployed application deployment
        try {
            ctx.handle("deployment undeploy " + cliTestApp1War.getName() + " --server-groups=" + sgOne);
            fail("Undeploying already undeployed application deployment doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("Deployment '" + cliTestApp1War.getName() +
                            "' is not found among the registered deployments."));
            // Verification wrong command execution fail - success
        }
    }

    /**
     * Testing undeploy already undeployed application deployments.
     * Using backward compatibility commands.
     * Verify if status of application deployments hasn't change.
     * Verify error message.
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testLegacyUndeployUndeployedDeployment() throws Exception {
        // Step 1) Deploy application deployment
        ctx.handle("deploy " + cliTestApp1War.getAbsolutePath() + " --server-groups=" + sgOne);

        // Step 2) Check if applications deployments is installed
        DeploymentInfoResult result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // Step 3) Undeploy application deployment from server group one
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --server-groups=" + sgOne);

        // Step 4) Check if applications deployments is correctly uninstalled
        result = deploymentInfo(cli, sgOne);
        checkMissing(result, cliTestApp1War.getName(), ctx);

        // Step 5) Try undeploy already undeployed application deployment
        try {
            ctx.handle("undeploy " + cliTestApp1War.getName() + " --server-groups=" + sgOne);
            fail("Undeploying already undeployed application deployment doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("Deployment '" + cliTestApp1War.getName() +
                            "' is not found among the registered deployments."));
            // Verification wrong command execution fail - success
        }
    }

    /**
     * Testing of disabling already disabled application deployment.
     * Verify error message.
     * <ul>
     * <li>Step 1) Deploy disabled application deployment</li>
     * <li>Step 2a) Verify if deployment are successful by list command</li>
     * <li>Step 2b) Verify if application deployment is disabled by info command</li>
     * <li>Step 3) Try disable already disabled application deployment</li>
     * </ul>
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testDisableAlreadyDisabledDeployment() throws Exception {
        String serverGroups = " --server-groups=" + sgOne + "," + sgTwo;
        // Step 1) Deploy disabled application deployment
        // Deploying application deployment in state disabled is not supported by domain mode
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath() + serverGroups);

        // Step 2a) Verify if deployment are successful by list command
        DeploymentInfoResult result = deploymentList(cli);
        checkExist(result, cliTestApp1War.getName(), ctx);

        // Step 2b) Verify if application deployment is present and enabled by info command
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // Step 3) Disable application deployment
        ctx.handle("deployment disable " + cliTestApp1War.getName() + serverGroups);

        // Step 4) Verify if application deployment is disabled by info command
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);

        // Step 5) Try disable already disabled application deployment
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorOutput));

        ctx.handle("deployment disable " + cliTestApp1War.getName() + serverGroups);

        //Check error message
        assertThat("Error message doesn't contains expected message information!"
                , errorOutput.toString(), allOf(
                        containsString("Deployment '" + cliTestApp1War.getName() + "' is already disabled in '" + sgOne + "'."),
                        containsString("Deployment '" + cliTestApp1War.getName() + "' is already disabled in '" + sgTwo + "'.")
                ));
        // Verification wrong command execution fail - success
    }

    /**
     * Testing of disabling already disabled application deployment.
     * Using backward compatibility commands.
     * Verify error message.
     * <ul>
     * <li>Step 1) Deploy disabled application deployment</li>
     * <li>Step 2a) Verify if deployment are successful by list command</li>
     * <li>Step 2b) Verify if application deployment is disabled by info command</li>
     * <li>Step 3) Try disable already disabled application deployment</li>
     * </ul>
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testLegacyDisableAlreadyDisabledDeployment() throws Exception {
        String serverGroups = " --server-groups=" + sgOne + "," + sgTwo;
        // Step 1) Deploy disabled application deployment
        // Deploying application deployment in state disabled is not supported by domain mode
        ctx.handle("deploy " + serverGroups + " " + cliTestApp1War.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        DeploymentInfoResult result = deploymentList(cli);
        checkExist(result, cliTestApp1War.getName(), ctx);

        // Step 2b) Verify if application deployment is is present and enabled by info command
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // Step 3) Disable application deployment
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content " + serverGroups);

        // Step 4) Verify if application deployment is disabled by info command
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);

        // Step 5) Try disable already disabled application deployment
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorOutput));

        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content " + serverGroups);

        //Check error message
        assertThat("Error message doesn't contains expected message information!"
                , errorOutput.toString(), allOf(
                        containsString("Deployment '" + cliTestApp1War.getName() + "' is already disabled in '" + sgOne + "'."),
                        containsString("Deployment '" + cliTestApp1War.getName() + "' is already disabled in '" + sgTwo + "'.")
                ));
        // Verification wrong command execution fail - success
    }

    /**
     * Testing of undeploying application deployment still registered in other server group.
     * Verify error message.
     *
     * <ul>
     * <li>Step 1) Deploy disabled application deployment</li>
     * <li>Step 2) Check if applications deployments is installed and in right state</li>
     * <li>Step 3) Undeploy application deployment from server group one</li>
     * <li>Step 4) Check if applications deployments is in right state</li>
     * <li>Step 5) Try undeploy already undeployed application deployment</li>
     * </ul>
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testUndeployDeploymentStillDeployedInOtherServerGroup() throws Exception {
        // Step 1) Deploy disabled application deployment
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath() + " --server-groups=" + sgOne + "," + sgTwo);

        // Step 2) Check if applications deployments is installed and in right state
        DeploymentInfoResult result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // Step 3) Disable application deployment from server group one
        ctx.handle("deployment disable " + cliTestApp1War.getName() + " --server-groups=" + sgOne);

        // Step 4) Check if applications deployments is in right state
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // Step 5) Try undeploy application deployment disabled in server group one and enabled in server group two
        try {
            ctx.handle("deployment undeploy " + cliTestApp1War.getName() + " --server-groups=" + sgTwo);
            fail("Undeploy application deployment between server groups doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("Cannot undeploy '" + cliTestApp1War.getName() +
                            "' as it is still deployed in [main-server-group]. Please specify all relevant server groups."));
            // Verification wrong command execution fail - success
        }
    }

    /**
     * Testing of undeploying application deployment still registered in other server group.
     * Using backward compatibility commands.
     * Verify error message.
     *
     * <ul>
     * <li>Step 1) Deploy disabled application deployment</li>
     * <li>Step 2) Check if applications deployments is installed and in right state</li>
     * <li>Step 3) Undeploy application deployment from server group one</li>
     * <li>Step 4) Check if applications deployments is in right state</li>
     * <li>Step 5) Try undeploy already undeployed application deployment</li>
     * </ul>
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testLegacyUndeployDeploymentStillDeployedInOtherServerGroup() throws Exception {
        // Step 1) Deploy application deployment in two server groups
        ctx.handle("deploy " + cliTestApp1War.getAbsolutePath() + " --server-groups=" + sgOne + "," + sgTwo);

        // Step 2) Check if applications deployments is installed and in right state
        DeploymentInfoResult result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);
        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // Step 3) Disable application deployment from server group one
        ctx.handle("undeploy " + cliTestApp1War.getName() + " --keep-content --server-groups=" + sgOne);

        // Step 4) Check if applications deployments is in right state
        result = deploymentInfo(cli, sgOne);
        checkExist(result, cliTestApp1War.getName(), ADDED, ctx);
        result = deploymentInfo(cli, sgTwo);
        checkExist(result, cliTestApp1War.getName(), ENABLED, ctx);

        // Step 5) Try undeploy application deployment disabled in server group one and enabled in server group two
        try {
            ctx.handle("undeploy " + cliTestApp1War.getName() + " --server-groups=" + sgTwo);
            fail("Undeploy application deployment between server groups doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("Cannot undeploy '" + cliTestApp1War.getName() +
                            "' as it is still deployed in [main-server-group]. Please specify all relevant server groups."));
            // Verification wrong command execution fail - success
        }
    }
}
