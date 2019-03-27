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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createCliArchive;
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createWarArchive;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.STOPPED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.OK;

import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentInfoResult;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import org.junit.AfterClass;

import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkExist;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkMissing;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.checkEmpty;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.deploymentInfo;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.deploymentList;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.legacyDeploymentInfo;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class DeployTestCase extends AbstractCliTestBase{

    private static final String WRONG_PATH_PART = "216561-d.war";
    private static final String WRONG_DEPLOYMENT = "testRo.war";

    private static File cliTestApp1War;
    private static File cliTestApp2War;
    private static File cliTestAnotherWar;
    private static File tempCliTestAppWar;

    private static CommandContext ctx;

    @BeforeClass
    public static void before() throws Exception {
        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setInitConsole(true).setConsoleInput(System.in).setConsoleOutput(System.out).
                setController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + TestSuiteEnvironment.getServerPort());
        ctx = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        ctx.connectController();
        AbstractCliTestBase.initCLI(TestSuiteEnvironment.getServerAddress());

        // deployment1
        cliTestApp1War = createWarArchive("cli-test-app1-deploy.war", "Version0");

        // deployment2
        cliTestApp2War = createWarArchive("cli-test-app2-deploy.war", "Version1");

        // deployment3
        cliTestAnotherWar = createWarArchive("cli-test-another-deploy.war", "Version2");
    }

    @AfterClass
    public static void after() throws Exception {
        ctx.terminateSession();
        AbstractCliTestBase.closeCLI();

        cliTestApp1War.delete();
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
    }

    @After
    public void afterTest() {
        ctx.handleSafe("deployment undeploy *");
        if (tempCliTestAppWar != null) {
            tempCliTestAppWar.delete();
        }
    }

    /**
     * Test verify a life cycle of deployment operation in singleton mode.
     * <ul>
     * <li>Step 1) Deploy applications deployments</li>
     * <li>Step 2a) Verify if deployment are successful by list command</li>
     * <li>Step 2b) Verify if applications deployments are enabled by info command</li>
     * <li>Step 3) Disabling selected application deployment</li>
     * <li>Step 4) Verify if selected application deployment is disabled, but other have still previous state</li>
     * <li>Step 5) Disable all deployed applications deployments</li>
     * <li>Step 6) Check if all applications deployments is disabled</li>
     * <li>Step 7) Enable selected application deployment</li>
     * <li>Step 8) Verify if selected application deployment are enabled, but other have still previous state</li>
     * <li>Step 9) Enable all applications deployments</li>
     * <li>Step 10) Verify if all applications deployments are enabled</li>
     * <li>Step 11) Undeploy one application deployment</li>
     * <li>Step 12) Check if selected application deployment is removed, but others still exist with right state</li>
     * <li>Step 13) Undeploy all applications deployments</li>
     * <li>Step 14) Check if all applications deployments is gone</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testDeploymentLiveCycle() throws Exception {
        // Step 1) Deploy applications deployments
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestApp2War.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        DeploymentInfoResult result = deploymentList(cli);
        checkExist(result, cliTestApp1War.getName(), ctx);
        checkExist(result, cliTestAnotherWar.getName());
        checkExist(result, cliTestApp2War.getName());

        // Step 2b) Verify if applications deployments are enabled by info command
        result = deploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), OK);
        checkExist(result, cliTestAnotherWar.getName(), OK);
        checkExist(result, cliTestApp2War.getName(), OK);

        // Step 3) Disabling selected application deployment
        ctx.handle("deployment disable " + cliTestApp1War.getName());

        // Step 4) Verify if selected application deployment is disabled, but other have still previous state
        result = deploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), STOPPED);
        checkExist(result, cliTestAnotherWar.getName(), OK);
        checkExist(result, cliTestApp2War.getName(), OK);

        // Step 5) Disable all deployed applications deployments
        ctx.handle("deployment disable-all");

        // Step 6) Check if all applications deployments is disabled
        result = deploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), STOPPED);
        checkExist(result, cliTestAnotherWar.getName(), STOPPED);
        checkExist(result, cliTestApp2War.getName(), STOPPED);

        // Step 7) Enable selected application deployment
        ctx.handle("deployment enable " + cliTestApp2War.getName());

        // Step 8) Verify if selected application deployment are enabled, but other have still previous state
        result = deploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), STOPPED);
        checkExist(result, cliTestAnotherWar.getName(), STOPPED);
        checkExist(result, cliTestApp2War.getName(), OK);

        // Step 9) Enable all applications deployments
        ctx.handle("deployment enable-all");

        // Step 10) Verify if all applications deployments are enabled
        result = deploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), OK);
        checkExist(result, cliTestAnotherWar.getName(), OK);
        checkExist(result, cliTestApp2War.getName(), OK);

        // Step 11) Undeploy one application deployment
        ctx.handle("deployment undeploy " + cliTestApp2War.getName());

        // Step 12) Check if selected application deployment is removed, but others still exist with right state
        result = deploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), OK);
        checkExist(result, cliTestAnotherWar.getName(), OK);
        checkMissing(result, cliTestApp2War.getName());

        // Step 13) Undeploy all applications deployments
        ctx.handle("deployment undeploy *");

        // Step 14) Check if all applications deployments is gone
        result = deploymentList(cli);
        checkMissing(result, cliTestApp1War.getName(), ctx);
        checkMissing(result, cliTestAnotherWar.getName(), ctx);
        checkMissing(result, cliTestApp2War.getName(), ctx);
        checkEmpty(result);
    }

    @Test
    public void testDeployAllCompletion() throws Exception {
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestApp2War.getAbsolutePath());

        {
            String cmd = "deploy --name=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.contains("*"));
            assertTrue(candidates.toString(), candidates.contains(cliTestApp1War.getName()));
            assertTrue(candidates.toString(), candidates.contains(cliTestAnotherWar.getName()));
            assertTrue(candidates.toString(), candidates.contains(cliTestApp2War.getName()));
        }

        {
            String cmd = "deploy --name=*";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.contains("* "));
            assertTrue(candidates.toString(), candidates.size() == 1);
        }
    }

    /**
     * Testing re-deploy of application deployment.
     * Using backward compatibility commands.
     * <ul>
     * <li>Step 1) Prepare application deployment archive</li>
     * <li>Step 2) Deploy application deployment</li>
     * <li>Step 3) Verify if application deployment is deployed and enabled by info command</li>
     * <li>Step 4) Delete previous application deployment archive and create new for redeploy</li>
     * <li>Step 5) Try redeploy application deployment</li>
     * <li>Step 6) Verify if application deployment is deployed and enabled by info command</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testLegacyRedeployFileDeployment() throws Exception {
        // Step 1) Prepare application deployment archive
        tempCliTestAppWar = createWarArchive("cli-test-app-redeploy.war", "VersionDeploy1.01");

        // Step 2) Deploy application deployment
        ctx.handle("deploy " + tempCliTestAppWar.getAbsolutePath());

        // Step 3) Verify if application deployment is deployed and enabled by info command
        DeploymentInfoResult result = legacyDeploymentInfo(cli);
        checkExist(result, tempCliTestAppWar.getName(), OK, ctx);

        // Step 4) Delete previous application deployment archive and create new for redeploy
        tempCliTestAppWar.delete();
        tempCliTestAppWar = createWarArchive("cli-test-app-redeploy.war", "VersionReDeploy2.02");

        // Step 5) Try redeploy application deployment
        ctx.handle("deploy --force " + tempCliTestAppWar.getAbsolutePath());

        // Step 6) Verify if application deployment is deployed and enabled by info command
        result = legacyDeploymentInfo(cli);
        checkExist(result, tempCliTestAppWar.getName(), OK, ctx);
        // TODO read page.html and check content
    }

    /**
     * Testing re-deploy of application deployment.
     * <ul>
     * <li>Step 1) Prepare application deployment archive</li>
     * <li>Step 2) Deploy application deployment</li>
     * <li>Step 3) Verify if application deployment is deployed and enabled by info command</li>
     * <li>Step 4) Delete previous application deployment archive and create new for redeploy</li>
     * <li>Step 5) Try redeploy application deployment</li>
     * <li>Step 6) Verify if application deployment is deployed and enabled by info command</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testRedeployFileDeployment() throws Exception {
        // Step 1) Prepare application deployment archive
        tempCliTestAppWar = createWarArchive("cli-test-app-redeploy.war", "VersionDeploy1.01");

        // Step 2) Deploy application deployment
        ctx.handle("deployment deploy-file " + tempCliTestAppWar.getAbsolutePath());

        // Step 3) Verify if application deployment is deployed and enabled by info command
        DeploymentInfoResult result = deploymentInfo(cli);
        checkExist(result, tempCliTestAppWar.getName(), OK, ctx);

        // Step 4) Delete previous application deployment archive and create new for redeploy
        tempCliTestAppWar.delete();
        tempCliTestAppWar = createWarArchive("cli-test-app-redeploy.war", "VersionReDeploy2.02");

        // Step 5) Try redeploy application deployment
        ctx.handle("deployment deploy-file --replace " + tempCliTestAppWar.getAbsolutePath());

        // Step 6) Verify if application deployment is deployed and enabled by info command
        result = deploymentInfo(cli);
        checkExist(result, tempCliTestAppWar.getName(), OK, ctx);
        // TODO read page.html and check content
    }

    /**
     * Deploy one application deployment via cli archive.
     * Using backward compatibility commands.
     *
     * @throws Exception
     */
    @Test
    public void testLegacyDeployUndeployViaCliArchive() throws Exception {
        tempCliTestAppWar = createCliArchive();
        ctx.handle("deploy " + tempCliTestAppWar.getAbsolutePath());
    }

    /**
     * Deploy one application deployment via cli archive
     *
     * @throws Exception
     */
    @Test
    public void testDeployUndeployViaCliArchive() throws Exception {
        tempCliTestAppWar = createCliArchive();
        ctx.handle("deployment deploy-cli-archive " + tempCliTestAppWar.getAbsolutePath());
    }

    /**
     * Deploy one application deployment via cli archive.
     * Operation is limited by 2 second only.
     * Using backward compatibility commands.
     *
     * @throws Exception
     */
    @Test
    public void testLegacyDeployUndeployViaCliArchiveWithTimeout() throws Exception {
        tempCliTestAppWar = createCliArchive();
        ctx.handle("command-timeout set 2"); // set num_seconds        - set the timeout to a number of seconds.
        ctx.handle("deploy " + tempCliTestAppWar.getAbsolutePath());
    }

    /**
     * Deploy one application deployment via cli archive.
     * Operation is limited by 2 second only.
     *
     * @throws Exception
     */
    @Test
    public void testDeployUndeployViaCliArchiveWithTimeout() throws Exception {
        tempCliTestAppWar = createCliArchive();
        ctx.handle("command-timeout set 2"); // Operation is limited by 2000 second only
        ctx.handle("deployment deploy-cli-archive " + tempCliTestAppWar.getAbsolutePath());
    }

    /**
     * Testing deploy disabled application deployments and try enable and disable again it.
     * Using backward compatibility commands.
     * <ul>
     * <li>Step 1) Deploy disabled 3 applications deployments</li>
     * <li>Step 2) Check if applications deployments is installed and disabled</li>
     * <li>Step 3) Enable all applications deployments</li>
     * <li>Step 4) Check if applications deployments is enabled</li>
     * <li>Step 5) Disable all applications deployments</li>
     * <li>Step 6) Check if applications deployments is disabled</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testLegacyDisableEnableDeployments() throws Exception {
        // Step 1) Deploy disabled 3 applications deployments
        ctx.handle("deploy --disabled " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --disabled " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --disabled " + cliTestApp2War.getAbsolutePath());

        // Step 2) Check if applications deployments is installed and disabled
        DeploymentInfoResult result = legacyDeploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), STOPPED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), STOPPED, ctx);
        checkExist(result, cliTestApp2War.getName(), STOPPED, ctx);

        // Step 3) Enable all applications deployments
        ctx.handle("deploy --name=*");

        // Step 4) Check if applications deployments is enabled
        result = legacyDeploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), OK, ctx);
        checkExist(result, cliTestAnotherWar.getName(), OK, ctx);
        checkExist(result, cliTestApp2War.getName(), OK, ctx);

        // Step 5) Disable all applications deployments
        ctx.handle("undeploy * --keep-content");

        // Step 6) Check if applications deployments is disabled
        result = legacyDeploymentInfo(cli);
        checkExist(result, cliTestApp1War.getName(), STOPPED, ctx);
        checkExist(result, cliTestAnotherWar.getName(), STOPPED, ctx);
        checkExist(result, cliTestApp2War.getName(), STOPPED, ctx);
    }

    /**
     * Testing deploy via URL.
     * Only tested by local file URL, testing via http/https can not rely on availability.
     *
     * @throws Exception
     */
    @Test
    public void testDeployViaUrl() throws Exception {
        // Deploy application deployment via url link
        ctx.handle("deployment deploy-url " + cliTestApp2War.toURI());

        // Check if application deployments is installed
        DeploymentInfoResult result = deploymentList(cli);
        checkExist(result, cliTestApp2War.getName(), ctx);
        result = deploymentInfo(cli);
        checkExist(result, cliTestApp2War.getName(), OK, ctx);
    }

    /**
     * Testing deploy application deployments with wrong path.
     * Verify if status of application deployments hasn't change.
     * Verify error message.
     *
     * @throws Exception
     */
    @Test
    public void testDeployFileWithWrongPath() throws Exception {
        // Remember status of application deployment before deploy operation
        final DeploymentInfoResult before = deploymentInfo(cli);

        // Try deploy application deployments with wrong path
        try {
            ctx.handle("deployment deploy-file " + cliTestApp2War.getPath() + WRONG_PATH_PART);
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("Path " + cliTestApp2War.getPath() + WRONG_PATH_PART + " doesn't exist."));
            // Verification wrong command execution fail - success
        }

        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli);
        if (!after.isOutputEmpty()) {
            assertThat("After deploying wrong path of application deployment something is deployed.",
                    after, is(before));
        }
    }

    /**
     * Testing deploy application deployments with wrong url.
     * Verify if status of application deployments hasn't change.
     * Verify error message.
     *
     * @throws Exception
     */
    @Test
    public void testDeployWithWrongUrl() throws Exception {
        // Remember status of application deployment before deploy operation
        final DeploymentInfoResult before = deploymentInfo(cli);

        // Use local file url
        try {
            ctx.handle("deployment deploy-url " + cliTestApp2War.toURI() + WRONG_PATH_PART);
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), allOf(containsString("WFLYSRV0150:"),
                            containsString("'" + cliTestApp2War.toURI() + WRONG_PATH_PART + "'")));
            // Verification wrong command execution fail - success
        }

        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli);
        if (!after.isOutputEmpty()) {
            assertThat("After deploying wrong url of application deployment something is deployed.",
                    after, is(before));
        }
    }

    /**
     * Try deploy cli archive with wrong path and wrong cli command.
     * Verify if status of application deployments hasn't change.
     * Verify error messages.
     *
     * @throws Exception
     */
    @Test
    public void testDeployWithWrongCli() throws Exception {
        final String wrongArgument = "--fddhgfhtsdgr";
        // Remember status of application deployment before deploy operation
        final DeploymentInfoResult before = deploymentInfo(cli);

        // Try deploy cli archive with wrong path
        tempCliTestAppWar = createCliArchive("ls "+ wrongArgument +" sgsfgfd ghf d");
        try {
            ctx.handle("deployment deploy-cli-archive " + tempCliTestAppWar.getPath() + WRONG_PATH_PART);
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString(tempCliTestAppWar.getPath() + WRONG_PATH_PART));
            // Verification wrong command execution fail - success
        }

        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli);
        if (!after.isOutputEmpty()) {
            assertThat("After deploying wrong path of cli archive something is deployed.",
                    after, is(before));
        }

        // Try deploy cli archive with wrong cli command in archive
        try {
            ctx.handle("deployment deploy-cli-archive " + tempCliTestAppWar.getPath());
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("[" + wrongArgument + "]"));
            // Verification wrong command execution fail - success
        }

        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after1 = deploymentInfo(cli);
        if (!after1.isOutputEmpty()) {
            assertThat("After deploying wrong cli archive something is deployed.",
                    after1, is(before));
        }
    }

    /**
     * Testing of enabling non-deployed application deployment.
     * Verify if status of application deployments hasn't change.
     * Verify error message.
     *
     * @throws Exception
     */
    @Test
    public void testEnableWrongDeployments() throws Exception {
        // Remember status of application deployment before deploy operation
        final DeploymentInfoResult before = deploymentInfo(cli);

        // Try enable non installed application deployment
        try {
            ctx.handle("deployment enable " + WRONG_DEPLOYMENT);
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), containsString("'" + WRONG_DEPLOYMENT + "'"));
            // Verification wrong command execution fail - success
        }

        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli);
        if (!after.isOutputEmpty()) {
            assertThat("After enable of non-deployed application deployment something is change.",
                    after, is(before));
        }
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
        // Deploying one additional deployment to verify that disabling of non-deployed application deployment does't have affect to others
        ctx.handle("deployment deploy-file " + cliTestAnotherWar.getAbsolutePath());
        // Remember status of application deployment before deploy operation
        final DeploymentInfoResult before = deploymentInfo(cli);
        checkExist(before, cliTestAnotherWar.getName(), OK, ctx);

        // Try disable non installed application deployment
        try {
            ctx.handle("deployment disable " + WRONG_DEPLOYMENT);
            fail("Disabling of non-deployed application deployment doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), allOf(containsString("WFLYCTL0216"),
                            containsString(WRONG_DEPLOYMENT)));
            // Verification wrong command execution fail - success
        }
        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli);
        assertThat("After disabling of non-deployed application deployment something is change!",
                after, is(before));
    }

    /**
     * Testing of disabling already disabled application deployment.
     * <ul>
     * <li>Step 1) Deploy disabled application deployment</li>
     * <li>Step 2) Verify if applications deployments is right state by info command</li>
     * <li>Step 3) Try disable already disabled application deployment</li>
     * <li>Step 4) Verify if is application deployment status hasn't change</li>
     * </ul>
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testDisableAlreadyDisabledDeployment() throws Exception {
        // Step 1) Deploy disabled application deployment
        ctx.handle("deployment deploy-file --disabled " + cliTestAnotherWar.getAbsolutePath());
        // Deploying one additional deployment to verify that disabling already disabled does't have affect to others
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath());

        // Step 2) Verify if applications deployments is right state by info command
        final DeploymentInfoResult before = deploymentInfo(cli);
        checkExist(before, cliTestApp1War.getName(), OK, ctx);
        checkExist(before, cliTestAnotherWar.getName(), STOPPED, ctx);

        // Step 3) Try disable already disabled application deployment
        try {
            ctx.handle("deployment disable " + cliTestAnotherWar.getName());
        } catch (Exception ex) {
            fail("Disabling already disabled application deployment FAILED! Expecting No error no warning \n" + ex.getMessage());
        }
        // Step 4) Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli);
        assertThat("After disabling of already disabled application deployment something is change!",
                after, is(before));
    }

    /**
     * Testing legacy disabling of non-deployed application deployments.
     * Verify if status of application deployments hasn't change.
     * Verify error message.
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testLegacyDisableWrongDeployment() throws Exception {
        // Deploying one additional deployment to verify that disabling of non-deployed application deployment does't have affect to others
        ctx.handle("deploy  " + cliTestAnotherWar.getAbsolutePath());
        // Remember status of application deployment before deploy operation
        final DeploymentInfoResult before = deploymentInfo(cli);
        checkExist(before, cliTestAnotherWar.getName(), OK, ctx);

        // Try disable non installed application deployment
        try {
            ctx.handle("undeploy --keep-content " + WRONG_DEPLOYMENT);
            fail("Disabling of non-deployed application deployment doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertThat("Error message doesn't contains expected message information!"
                    , ex.getMessage(), allOf(containsString("WFLYCTL0216"),
                            containsString(WRONG_DEPLOYMENT)));
            // Verification wrong command execution fail - success
        }
        // Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli);
        assertThat("After disabling of non-deployed application deployment something is change!",
                after, is(before));
    }

    /**
     * Testing of legacy disabling already disabled application deployment.
     * <ul>
     * <li>Step 1) Deploy disabled application deployment</li>
     * <li>Step 2) Verify if applications deployments is right state by info command</li>
     * <li>Step 3) Try disable already disabled application deployment</li>
     * <li>Step 4) Verify if is application deployment status hasn't change</li>
     * </ul>
     *
     * @throws Exception
     * @see <a href="https://issues.jboss.org/browse/WFCORE-3566">WFCORE-3566</a>
     */
    @Test
    public void testLegacyDisableAlreadyDisabledDeployment() throws Exception {
        // Step 1) Deploy disabled application deployment
        ctx.handle("deploy --disabled " + cliTestAnotherWar.getAbsolutePath());
        // Deploying one additional deployment to verify that disabling already disabled does't have affect to others
        ctx.handle("deploy " + cliTestApp1War.getAbsolutePath());

        // Step 2) Verify if applications deployments is right state by info command
        final DeploymentInfoResult before = deploymentInfo(cli);
        checkExist(before, cliTestApp1War.getName(), OK, ctx);
        checkExist(before, cliTestAnotherWar.getName(), STOPPED, ctx);

        // Step 3) Try disable already disabled application deployment
        try {
            ctx.handle("undeploy --keep-content " + cliTestAnotherWar.getName());
        } catch (Exception ex) {
            fail("Disabling already disabled application deployment FAILED! Expecting No error no warning \n" + ex.getMessage());
        }
        // Step 4) Verify if is application deployment status hasn't change
        final DeploymentInfoResult after = deploymentInfo(cli);
        assertThat("After disabling of already disabled application deployment something is change!",
                after, is(before));
    }

    @Test
    public void testDeployInBatch() throws Exception {
        ctx.handle("batch");
        try {
            ctx.handle("deploy " + cliTestApp1War.getAbsolutePath() + " --runtime-name=calendar.war --name=calendar.war --disabled --unmanaged");
            ctx.handle("run-batch");
        } catch (Exception ex) {
            try {
                ctx.handle("discard-batch");
            } catch (Exception ex2) {
                // XXX OK, the batch failed but terminated.
            }
            throw ex;
        }
    }

    @Test
    public void testForceDeployInBatch() throws Exception {
        ctx.handle("deploy " + cliTestApp1War.getAbsolutePath() +" --runtime-name=calendar.war --name=calendar.war --disabled --unmanaged");
        ctx.handle("batch");
        try {
            ctx.handle("deploy " + cliTestApp1War.getAbsolutePath() + " --runtime-name=calendar.war --name=calendar.war --disabled --unmanaged --force");
            ctx.handle("run-batch");
        } catch (Exception ex) {
            try {
                ctx.handle("discard-batch");
            } catch (Exception ex2) {
                // XXX OK, the batch failed but terminated.
            }
            throw ex;
        }
    }

    @Test
    public void testDeployFileInBatch() throws Exception {
        ctx.handle("batch");
        try {
            ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath() + " --runtime-name=calendar.war --name=calendar.war --disabled --unmanaged");
            ctx.handle("run-batch");
        } catch (Exception ex) {
            try {
                ctx.handle("discard-batch");
            } catch (Exception ex2) {
                // XXX OK, the batch failed but terminated.
            }
            throw ex;
        }
    }

    @Test
    public void testForceDeployFileInBatch() throws Exception {
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath() +" --runtime-name=calendar.war --name=calendar.war --disabled --unmanaged");
        ctx.handle("batch");
        try {
            ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath() +" --runtime-name=calendar.war --name=calendar.war --disabled --unmanaged --replace");
            ctx.handle("run-batch");
        } catch(Exception ex) {
            try {
                ctx.handle("discard-batch");
            } catch(Exception ex2) {
                // XXX OK, the batch failed but terminated.
            }
            throw ex;
        }
    }
}
