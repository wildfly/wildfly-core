/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.domain.management.cli;

import static org.jboss.as.test.deployment.DeploymentArchiveUtils.createCliArchive;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Iterator;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test case for https://issues.redhat.com/browse/WFCORE-5029
 *
 * @author wangc
 *
 */
public class ServerGroupDeploymentManagedAttributeTestCase extends AbstractCliTestBase {

    protected static File testAppWar;
    protected static CommandContext ctx;
    protected static DomainTestSupport testSupport;

    protected static String sg;

    @BeforeClass
    public static void before() throws Exception {
        testSupport = CLITestSuite.createSupport(ServerGroupDeploymentManagedAttributeTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.primaryAddress);

        // deployment
        testAppWar = createCliArchive("testAppWar-deploy-all.war", "Version0");

        final Iterator<String> sgI = CLITestSuite.serverGroups.keySet().iterator();
        if (!sgI.hasNext()) {
            fail("Server groups aren't available.");
        }
        sg = sgI.next();
    }

    @AfterClass
    public static void after() throws Exception {
        ctx.handleSafe("deployment undeploy * --all-relevant-server-groups");

        CLITestSuite.stopSupport();
        AbstractCliTestBase.closeCLI();

        testAppWar.delete();
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

    @Test
    public void testManagedAttributeValue() throws Exception {
        cli.sendLine("deploy --server-groups=" + sg + " " + testAppWar.getAbsolutePath());
        cli.sendLine("/server-group=" + sg + "/deployment=" + testAppWar.getName() + ":read-attribute(name=managed)");
        String output = cli.readOutput();
        assertTrue("Server group deployment managed value is incorrect in " + output, output.contains("true"));
    }

    @Test
    public void testUnmanagedAttributeValue() throws Exception {
        cli.sendLine("deploy --server-groups=" + sg + " " + testAppWar.getAbsolutePath() + " --unmanaged");
        cli.sendLine("/server-group=" + sg + "/deployment=" + testAppWar.getName() + ":read-attribute(name=managed)");
        String output = cli.readOutput();
        assertTrue("Server group deployment managed value is incorrect in " + output, output.contains("false"));
    }
}
