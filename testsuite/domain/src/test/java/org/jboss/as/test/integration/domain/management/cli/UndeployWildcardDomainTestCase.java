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

import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createEnterpriseArchive;
import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createWarArchive;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.ENABLED;
import static org.jboss.as.test.deployment.DeploymentInfoUtils.DeploymentState.NOT_ADDED;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Iterator;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.deployment.DeploymentInfoUtils;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


/**
 * @author Alexey Loubyansky
 */
public class UndeployWildcardDomainTestCase {

    private static String EXPECTED_ERROR_MESSAGE = "No deployment matched wildcard expression mar";

    private static File cliTestApp1War;
    private static File cliTestApp2War;
    private static File cliTestAnotherWar;
    private static File cliTestAppEar;
    private static File diffCliPersistentTestWars;

    private static String sgOne;
    private static String sgTwo;

    private CommandContext ctx;
    private static DomainTestSupport testSupport;
    private static DeploymentInfoUtils infoUtils;

    @BeforeClass
    public static void before() throws Exception {
        testSupport = CLITestSuite.createSupport(UndeployWildcardDomainTestCase.class.getSimpleName());
        infoUtils = new DeploymentInfoUtils(DomainTestSupport.masterAddress);
        infoUtils.connectCli();

        // deployment1
        cliTestApp1War = createWarArchive("cli-test-app1.war", "Version0");

        // deployment2
        cliTestApp2War = createWarArchive("cli-test-app2.war", "Version1");

        // deployment3
        cliTestAnotherWar = createWarArchive("cli-test-another.war", "Version2");

        // deployment4
        cliTestAppEar = createEnterpriseArchive("cli-test-app.ear", "cli-test-app3.war", "Version3");

        // different deployment5
        diffCliPersistentTestWars = createWarArchive("diffcli-persistendtest.wars", "Persitend");

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

        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());

        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + diffCliPersistentTestWars.getAbsolutePath());

        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());
    }

    @After
    public void afterTest() throws Exception {
        ctx.handleSafe("deployment undeploy * --all-relevant-server-groups");

        ctx.terminateSession();
        infoUtils.resetDoubleCheck();
    }

    /**
     * Test undeploying with all relevant server groups using Aesh commands
     * Undeploy all deployed war archive
     *
     * @throws Exception
     */
    @Test
    public void testUndeployAllWars() throws Exception {
        ctx.handle("deployment undeploy *.war --all-relevant-server-groups");

        infoUtils.readDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using Aesh commands
     * Undeploy all deployed application deployments with filtering by prefix 'cli-test-app'
     *
     * @throws Exception
     */
    @Test
    public void testUndeployCliTestApps() throws Exception {
        ctx.handle("deployment undeploy cli-test-app* --all-relevant-server-groups");

        infoUtils.readDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using Aesh commands
     * Undeploy all deployed application deployments with prefix '*test-a*'
     *
     * @throws Exception
     */
    @Test
    public void testUndeployTestAs() throws Exception {
        ctx.handle("deployment undeploy *test-a* --all-relevant-server-groups");

        infoUtils.readDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using Aesh commands
     * Undeploy all deployed application deployments with filtering by '*test-a*.war'
     *
     * @throws Exception
     */
    @Test
    public void testUndeployTestAWARs() throws Exception {
        ctx.handle("deployment undeploy *test-a*.war --all-relevant-server-groups");

        infoUtils.readDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using backward compatibility commands
     * Undeploy all deployed war archive
     *
     * @throws Exception
     */
    @Test
    public void testLegacyUndeployAllWars() throws Exception {
        ctx.handle("undeploy *.war --all-relevant-server-groups");

        infoUtils.readLegacyDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readLegacyDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using backward compatibility commands
     * Undeploy all deployed application deployments with filtering by prefix 'cli-test-app'
     *
     * @throws Exception
     */
    @Test
    public void testLegacyUndeployCliTestApps() throws Exception {
        ctx.handle("undeploy cli-test-app* --all-relevant-server-groups");

        infoUtils.readLegacyDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readLegacyDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using backward compatibility commands
     * Undeploy all deployed application deployments with prefix '*test-ap*'
     *
     * @throws Exception
     */
    @Test
    public void testLegacyUndeployTestAps() throws Exception {
        ctx.handle("undeploy *test-ap* --all-relevant-server-groups");

        infoUtils.readLegacyDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), ENABLED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readLegacyDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkExistInOutputMemory(cliTestAnotherWar.getName(), NOT_ADDED);
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using backward compatibility commands
     * Undeploy all deployed application deployments with prefix '*test-a*'
     *
     * @throws Exception
     */
    @Test
    public void testLegacyUndeployTestAs() throws Exception {
        ctx.handle("undeploy *test-a* --all-relevant-server-groups");

        infoUtils.readLegacyDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readLegacyDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using Aesh commands
     * Undeploy all deployed application deployments with prefix 'test-a'
     *
     * @throws Exception
     */
    @Test
    public void testUndeployTestAps() throws Exception {
        ctx.handle("deployment undeploy *test-a* --all-relevant-server-groups");

        infoUtils.readDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using backward compatibility commands
     * Undeploy all deployed application deployments with filtering by '*test-a*.war'
     *
     * @throws Exception
     */
    @Test
    public void testLegacyUndeployTestAWARs() throws Exception {
        ctx.handle("undeploy *test-a*.war --all-relevant-server-groups");

        infoUtils.readLegacyDeploymentInfo(sgOne);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), NOT_ADDED);

        infoUtils.readLegacyDeploymentInfo(sgTwo);
        infoUtils.checkMissingInOutputMemory(cliTestApp1War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAnotherWar.getName());
        infoUtils.checkMissingInOutputMemory(cliTestApp2War.getName());
        infoUtils.checkMissingInOutputMemory(cliTestAppEar.getName());
        infoUtils.checkExistInOutputMemory(diffCliPersistentTestWars.getName(), ENABLED);
    }

    /**
     * Test undeploying with all relevant server groups using backward compatibility commands
     * Undeploy no one deployed application deployments with filtering by 'mar*'
     *
     * @throws Exception
     */
    @Test
    public void testUndeployNothing() throws Exception {
        // Try undeploy nothing with wrong wildcard
        try {
            ctx.handle("deployment undeploy mar* --all-relevant-server-groups");
            fail("Undeploying application deployment with wrong wildcard doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertTrue("Error message doesn't contains expected string! Expected string:\n"
                    + EXPECTED_ERROR_MESSAGE, ex.getMessage().contains(EXPECTED_ERROR_MESSAGE));
            // Verification wrong command execution fail - success
        }
    }

    /**
     * Test undeploying with all relevant server groups using Aesh commands
     * Undeploy no one deployed application deployments with filtering by 'mar*'
     *
     * @throws Exception
     */
    @Test
    public void testLegacyUndeployNothing() throws Exception {
        // Try undeploy nothing with wrong wildcard
        try {
            ctx.handle("undeploy mar* --all-relevant-server-groups");
            fail("Undeploying application deployment with wrong wildcard doesn't failed! Command execution fail is expected.");
        } catch (Exception ex) {
            // Check error message
            assertTrue("Error message doesn't contains expected string! Expected string:\n"
                    + EXPECTED_ERROR_MESSAGE, ex.getMessage().contains(EXPECTED_ERROR_MESSAGE));
            // Verification wrong command execution fail - success
        }
    }
}
