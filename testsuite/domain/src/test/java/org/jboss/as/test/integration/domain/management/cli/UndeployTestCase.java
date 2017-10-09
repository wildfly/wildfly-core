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
import static org.jboss.as.cli.Util.DEPLOYMENT;
import static org.jboss.as.cli.Util.ENABLED;
import static org.jboss.as.cli.Util.NAME;
import static org.jboss.as.cli.Util.READ_ATTRIBUTE;
import static org.jboss.as.cli.Util.SERVER_GROUP;
import static org.jboss.as.cli.Util.isSuccess;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class UndeployTestCase {

    private static File cliTestApp1War;

    private static String sgOne;
    private static String sgTwo;

    private CommandContext ctx;
    private static DomainTestSupport testSupport;

    @BeforeClass
    public static void before() throws Exception {

        testSupport = CLITestSuite.createSupport(UndeployTestCase.class.getSimpleName());

        String tempDir = System.getProperty("java.io.tmpdir");

        // deployment1
        WebArchive war = ShrinkWrap.create(WebArchive.class, "cli-undeploy-test-app1.war");
        war.addAsWebResource(new StringAsset("Version0"), "page.html");
        cliTestApp1War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp1War, true);

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

        cliTestApp1War.delete();
    }

    @Before
    public void beforeTest() throws Exception {
        ctx = CLITestUtil.getCommandContext(testSupport);
        ctx.connectController();

        ctx.handle("deploy --server-groups=" + sgOne + ',' + sgTwo + " " + cliTestApp1War.getAbsolutePath());

    }

    @After
    public void afterTest() throws Exception {
        ctx.terminateSession();
    }

    @Test
    public void undeploy() throws Exception {
        // From serverGroup1 only, still referenced from sg2. Must keep-content
        ctx.handle("undeploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());

        checkState(sgOne, cliTestApp1War.getName(), false);
        checkState(sgTwo, cliTestApp1War.getName(), true);

        ctx.handle("deployment undeploy * --all-relevant-server-groups");
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ',' + sgTwo + " " + cliTestApp1War.getAbsolutePath());

        checkState(sgOne, cliTestApp1War.getName(), true);
        checkState(sgTwo, cliTestApp1War.getName(), true);

        // From serverGroup1 only, still referenced from sg2. Must keep-content
        ctx.handle("deployment undeploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getName());

        checkState(sgOne, cliTestApp1War.getName(), false);
        checkState(sgTwo, cliTestApp1War.getName(), true);
    }

    private void checkState(String groupName, String deploymentname, boolean expected) throws Exception {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.addNode(SERVER_GROUP, groupName);
        builder.addNode(DEPLOYMENT, deploymentname);
        builder.setOperationName(READ_ATTRIBUTE);
        builder.addProperty(NAME, ENABLED);
        ModelNode request;
        request = builder.buildRequest();

        ModelNode outcome = ctx.getModelControllerClient().execute(request);
        if (isSuccess(outcome)) {
            if (!outcome.hasDefined("result")) {
                throw new Exception("No result");
            }
            if (outcome.get("result").asBoolean() != expected) {
                throw new Exception("Not expected " + expected + " for "
                        + groupName + " " + deploymentname);
            }
        } else {
            throw new Exception("Not success " + outcome);
        }
    }
}
