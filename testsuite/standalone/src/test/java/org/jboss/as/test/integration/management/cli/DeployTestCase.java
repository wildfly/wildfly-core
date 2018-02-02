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

import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createCliArchive;
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createWarArchive;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.STOPPED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.OK;

import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.deployment.DeploymentInfoUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import org.junit.AfterClass;

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
public class DeployTestCase {

    private static File cliTestApp1War;
    private static File cliTestApp2War;
    private static File cliTestAnotherWar;
    private static File tempCliTestAppWar;

    private static DeploymentInfoUtils infoUtils;
    private static CommandContext ctx;

    @BeforeClass
    public static void before() throws Exception {
        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setInitConsole(true).setConsoleInput(System.in).setConsoleOutput(System.out).
                setController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + TestSuiteEnvironment.getServerPort());
        ctx = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        ctx.connectController();
        infoUtils = new DeploymentInfoUtils(TestSuiteEnvironment.getServerAddress());
        infoUtils.connectCli();
        infoUtils.enableDoubleCheck(ctx);

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
        infoUtils.disconnectCli();

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

    @Test
    public void testDeploymentLiveCycle() throws Exception {
        // Step 1) Deploy applications deployments
        ctx.handle("deployment deploy-file " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file " + cliTestApp2War.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        infoUtils.checkDeploymentByList(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName());

        // Step 2b) Verify if applications deployments are enabled by info command
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 3a) Disabling selected application deployment
        ctx.handle("deployment disable " + cliTestApp1War.getName());

        // Step 4) Verify if selected application deployment is disabled, but other have still previous state
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 5) Disable all deployed applications deployments
        ctx.handle("deployment disable-all");

        // Step 6) Check if all applications deployments is disabled
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), STOPPED);

        // Step 7) Enable selected application deployment
        ctx.handle("deployment enable " + cliTestApp2War.getName());

        // Step 8) Verify if selected application deployment are enabled, but other have still previous state
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 9) Enable all applications deployments
        ctx.handle("deployment enable-all");

        // Step 10) Verify if all applications deployments are enabled
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 11) Undeploy one application deployment
        ctx.handle("deployment undeploy " + cliTestApp2War.getName());

        // Step 12) Check if selected application deployment is removed, but others still exist with right state
        infoUtils.checkDeploymentByInfo(cliTestApp1War.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());

        // Step 13) Undeploy all applications deployments
        ctx.handle("deployment undeploy *");

        // Step 14) Check if all applications deployments is gone
        infoUtils.readDeploymentList();
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
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

    @Test
    public void testLegacyRedeployFileDeployment() throws Exception {
        // Step 1) Prepare application deployment archive
        tempCliTestAppWar = createWarArchive("cli-test-app-redeploy.war", "VersionDeploy1.01");

        // Step 2) Deploy application deployment
        ctx.handle("deploy " + tempCliTestAppWar.getAbsolutePath());

        // Step 3) Verify if application deployment is deployed and enabled by info command
        infoUtils.checkDeploymentByLegacyInfo(tempCliTestAppWar.getName(), OK);

        // Step 4) Delete previous application deployment archive and create new for redeploy
        tempCliTestAppWar.delete();
        tempCliTestAppWar = createWarArchive("cli-test-app-redeploy.war", "VersionReDeploy2.02");

        // Step 5) Try redeploy application deployment
        ctx.handle("deploy --force " + tempCliTestAppWar.getAbsolutePath());

        // Step 6) Verify if application deployment is deployed and enabled by info command
        infoUtils.checkDeploymentByLegacyInfo(tempCliTestAppWar.getName(), OK);
        // TODO read page.html and check content
    }

    @Test
    public void testRedeployFileDeployment() throws Exception {
        // Step 1) Prepare application deployment archive
        tempCliTestAppWar = createWarArchive("cli-test-app-redeploy.war", "VersionDeploy1.01");

        // Step 2) Deploy application deployment
        ctx.handle("deployment deploy-file " + tempCliTestAppWar.getAbsolutePath());

        // Step 3) Verify if application deployment is deployed and enabled by info command
        infoUtils.checkDeploymentByInfo(tempCliTestAppWar.getName(), OK);

        // Step 4) Delete previous application deployment archive and create new for redeploy
        tempCliTestAppWar.delete();
        tempCliTestAppWar = createWarArchive("cli-test-app-redeploy.war", "VersionReDeploy2.02");

        // Step 5) Try redeploy application deployment
        ctx.handle("deployment deploy-file --replace " + tempCliTestAppWar.getAbsolutePath());

        // Step 6) Verify if application deployment is deployed and enabled by info command
        infoUtils.checkDeploymentByInfo(tempCliTestAppWar.getName(), OK);
        // TODO read page.html and check content
    }

    @Test
    public void testLegacyDeployUndeployViaCliArchive() throws Exception {
        /*
        Deploy one application deployment via cli archive
        Using backward compatibility commands
         */
        tempCliTestAppWar = createCliArchive();
        ctx.handle("deploy " + tempCliTestAppWar.getAbsolutePath());
    }

    @Test
    public void testDeployUndeployViaCliArchive() throws Exception {
        /*
        Deploy one application deployment via cli archive
         */
        tempCliTestAppWar = createCliArchive();
        ctx.handle("deployment deploy-cli-archive " + tempCliTestAppWar.getAbsolutePath());
    }

    @Test
    public void testLegacyDeployUndeployViaCliArchiveWithTimeout() throws Exception {
        /*
        Operation is limited by 2000 second only // realy? 2000? set num_seconds        - set the timeout to a number of seconds.
        Deploy one application deployment via cli archive
        Using backward compatibility commands
         */
        tempCliTestAppWar = createCliArchive();
        ctx.handle("command-timeout set 2");
        ctx.handle("deploy " + tempCliTestAppWar.getAbsolutePath());
    }

    @Test
    public void testDeployUndeployViaCliArchiveWithTimeout() throws Exception {
        /*
        Operation is limited by 2000 second only // realy? 2000? set num_seconds        - set the timeout to a number of seconds.
        Deploy one application deployment via cli archive
         */
        tempCliTestAppWar = createCliArchive();
        ctx.handle("command-timeout set 2");
        ctx.handle("deployment deploy-cli-archive " + tempCliTestAppWar.getAbsolutePath());
    }

    @Test
    public void testLegacyDisableEnableDeployments() throws Exception {
        // Step 1) Deploy disabled 3 applications deployments
        ctx.handle("deploy --disabled " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --disabled " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --disabled " + cliTestApp2War.getAbsolutePath());

        // Step 2) Check if applications deployments is installed and disabled
        infoUtils.checkDeploymentByLegacyInfo( cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), STOPPED);

        // Step 3) Enable all applications deployments
        ctx.handle("deploy --name=*");

        // Step 4) Check if applications deployments is enabled
        infoUtils.checkDeploymentByLegacyInfo(cliTestApp1War.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), OK);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), OK);

        // Step 5) Disable all applications deployments
        ctx.handle("undeploy * --keep-content");

        // Step 6) Check if applications deployments is disabled
        infoUtils.checkDeploymentByLegacyInfo(cliTestApp1War.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), STOPPED);
        infoUtils.checkExistInOutputMemory(cliTestApp2War.getName(), STOPPED);
    }

    @Test
    public void testDeployViaUrl() throws Exception {
        // Deploy application deployment via url link
        ctx.handle("deployment deploy-url " + cliTestApp2War.toURI());

        // Check if application deployments is installed
        infoUtils.checkDeploymentByList(cliTestApp2War.getName());
        infoUtils.checkDeploymentByInfo(cliTestApp2War.getName(), OK);
    }

    @Test
    public void testDeployFileWithWrongPath() {
        final String EXPECTED_URL_ERROR_MESSAGE = "doesn't exist";
        // Try deploy application deployments with wrong path
        try {
            ctx.handle("deployment deploy-file " + cliTestApp2War.getPath() + "89.war");
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertTrue("Error message doesn't contains expected string! Expected string:\n"
                    + EXPECTED_URL_ERROR_MESSAGE, ex.getMessage().contains(EXPECTED_URL_ERROR_MESSAGE));
            // Verification wrong command execution fail - success
        }
    }

    @Test
    public void testDeployWithWrongUrl() throws Exception {
        final String EXPECTED_URL_ERROR_MESSAGE = "Cannot create input stream from URL";
        // Use local file url
        ctx.handle("deployment deploy-url " + cliTestApp2War.toURI());

        infoUtils.checkDeploymentByInfo(cliTestApp2War.getName(), OK);
        try {
            ctx.handle("deployment deploy-url " + cliTestApp2War.toURI() + "89.war");
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertTrue("Error message doesn't contains expected string! Expected string:\n"
                    + EXPECTED_URL_ERROR_MESSAGE, ex.getMessage().contains(EXPECTED_URL_ERROR_MESSAGE));
            // Verification wrong command execution fail - success
        }
    }

    @Test
    public void testDeployWithWrongCli() {
        final String EXPECTED_CLI_ERROR_MESSAGE_WRONG_PATH = "doesn't exist";
        final String EXPECTED_CLI_ERROR_MESSAGE_WRONG_ARCHIVE = "Unrecognized arguments";

        tempCliTestAppWar = createCliArchive("ls -fsdg sgsfgfd ghf d");
        // Try deploy application deployments with wrong path
        try {
            ctx.handle("deployment deploy-cli-archive " + tempCliTestAppWar.getPath() + "216561.war");
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertTrue("Error message doesn't contains expected string! Expected string:\n"
                    + EXPECTED_CLI_ERROR_MESSAGE_WRONG_PATH, ex.getMessage().contains(EXPECTED_CLI_ERROR_MESSAGE_WRONG_PATH));
            // Verification wrong command execution fail - success
        }
        // Try deploy application deployments with wrong cli command in archive
        try {
            ctx.handle("deployment deploy-cli-archive " + tempCliTestAppWar.getPath());
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertTrue("Error message doesn't contains expected string! Expected string:\n"
                    + EXPECTED_CLI_ERROR_MESSAGE_WRONG_ARCHIVE, ex.getMessage().contains(EXPECTED_CLI_ERROR_MESSAGE_WRONG_ARCHIVE));
            // Verification wrong command execution fail - success
        }
    }

    @Test
    public void testDisableWrongDeployment() {
        final String EXPECTED_ERROR_MESSAGE = "WFCORE-3566";

        // Try disable non installed application deployment
        try {
            ctx.handle("deployment disable testRo.war");
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            // TODO Uncomment after fix WFCORE-3566
//            assertTrue("Error message doesn't contains expected string! Expected string:\n"
//                    + EXPECTED_ERROR_MESSAGE, ex.getMessage().contains(EXPECTED_ERROR_MESSAGE));
            // #WFCORE-3566
            // Verification wrong command execution fail - success
        }
    }

    @Test
    public void testDisableAlreadyDisabledDeployment() throws Exception {
        final String EXPECTED_ERROR_MESSAGE = "WFCORE-3566";
        // Step 1) Deploy disabled application deployment
        ctx.handle("deployment deploy-file --disabled " + cliTestAnotherWar.getAbsolutePath());

        // Step 2a) Verify if deployment are successful by list command
        infoUtils.checkDeploymentByList(cliTestAnotherWar.getName());

        // Step 2b) Verify if application deployment is disabled by info command
        infoUtils.checkDeploymentByInfo(cliTestAnotherWar.getName(), STOPPED);

        // Step 3a) Try disable already disabled application deployment
        try {
            ctx.handle("deployment disable " + cliTestAnotherWar.getName());
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            // TODO Uncomment after fix WFCORE-3566
//            assertTrue("Error message doesn't contains expected string! Expected string:\n"
//                    + EXPECTED_ERROR_MESSAGE, ex.getMessage().contains(EXPECTED_ERROR_MESSAGE));
            // #WFCORE-3566
            // Verification wrong command execution fail - success
        }
    }

    @Test
    public void testEnableWrongDeployments() {
        final String EXPECTED_ERROR_MESSAGE = "is not found among the registered deployments";

        // Try enable non installed application deployment
        try {
            ctx.handle("deployment enable testRo.war");
            fail("Deploying application deployment with wrong url link doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertTrue("Error message doesn't contains expected string! Expected string:\n"
                    + EXPECTED_ERROR_MESSAGE, ex.getMessage().contains(EXPECTED_ERROR_MESSAGE));
            // Verification wrong command execution fail - success
        }
    }
}
