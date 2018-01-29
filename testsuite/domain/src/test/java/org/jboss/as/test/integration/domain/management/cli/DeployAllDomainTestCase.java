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
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import static org.jboss.as.cli.Util.RESULT;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.jboss.shrinkwrap.impl.base.path.BasicPath;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class DeployAllDomainTestCase {

    protected static File cliTestApp1War;
    protected static File cliTestApp2War;
    protected static File cliTestAnotherWar;
    protected static File cliTestAppEar;

    protected static String sgOne;
    protected static String sgTwo;

    protected CommandContext ctx;
    protected static DomainTestSupport testSupport;

    @BeforeClass
    public static void before() throws Exception {

        testSupport = CLITestSuite.createSupport(UndeployWildcardDomainTestCase.class.getSimpleName());

        String tempDir = System.getProperty("java.io.tmpdir");

        // deployment1
        WebArchive war = ShrinkWrap.create(WebArchive.class, "cli-test-app1-deploy-all.war");
        war.addAsWebResource(new StringAsset("Version0"), "page.html");
        cliTestApp1War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp1War, true);

        // deployment2
        war = ShrinkWrap.create(WebArchive.class, "cli-test-app2-deploy-all.war");
        war.addAsWebResource(new StringAsset("Version1"), "page.html");
        cliTestApp2War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp2War, true);

        // deployment3
        war = ShrinkWrap.create(WebArchive.class, "cli-test-another-deploy-all.war");
        war.addAsWebResource(new StringAsset("Version2"), "page.html");
        cliTestAnotherWar = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestAnotherWar, true);

        // deployment4
        war = ShrinkWrap.create(WebArchive.class, "cli-test-app3-deploy-all.war");
        war.addAsWebResource(new StringAsset("Version3"), "page.html");
        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class,
                "cli-test-app-deploy-all.ear");
        ear.add(war, new BasicPath("/"), ZipExporter.class);
        cliTestAppEar = new File(tempDir + File.separator + ear.getName());
        new ZipExporterImpl(ear).exportTo(cliTestAppEar, true);

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
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
        cliTestAppEar.delete();
    }

    @Before
    public void beforeTest() throws Exception {
        ctx = CLITestUtil.getCommandContext(testSupport);
        ctx.connectController();

        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deploy --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Disable them all.
        ctx.handle("undeploy * --keep-content --all-relevant-server-groups");
    }

    @After
    public void afterTest() throws Exception {
        ctx.terminateSession();
    }

    @Test
    public void testDeployAll() throws Exception {
        checkDeployment(sgOne, cliTestApp1War.getName(), false);
        checkDeployment(sgOne, cliTestAnotherWar.getName(), false);
        checkDeployment(sgOne, cliTestAppEar.getName(), false);

        checkDeployment(sgTwo, cliTestApp2War.getName(), false);
        checkDeployment(sgTwo, cliTestAppEar.getName(), false);
        // Deploy them all.
        ctx.handle("deploy --name=* --server-groups=" + sgTwo + ',' + sgOne);
        checkDeployment(sgOne, cliTestApp1War.getName(), true);
        checkDeployment(sgOne, cliTestAnotherWar.getName(), true);
        checkDeployment(sgOne, cliTestAppEar.getName(), true);

        checkDeployment(sgTwo, cliTestApp2War.getName(), true);
        checkDeployment(sgTwo, cliTestAppEar.getName(), true);

        ctx.handle("deployment disable-all --all-relevant-server-groups");

        checkDeployment(sgOne, cliTestApp1War.getName(), false);
        checkDeployment(sgOne, cliTestAnotherWar.getName(), false);
        checkDeployment(sgOne, cliTestAppEar.getName(), false);

        checkDeployment(sgTwo, cliTestApp2War.getName(), false);
        checkDeployment(sgTwo, cliTestAppEar.getName(), false);
        // Deploy them all.
        ctx.handle("deployment enable-all --server-groups=" + sgTwo + ',' + sgOne);
        checkDeployment(sgOne, cliTestApp1War.getName(), true);
        checkDeployment(sgOne, cliTestAnotherWar.getName(), true);
        checkDeployment(sgOne, cliTestAppEar.getName(), true);

        checkDeployment(sgTwo, cliTestApp2War.getName(), true);
        checkDeployment(sgTwo, cliTestAppEar.getName(), true);
    }

    private void checkDeployment(String serverGroup, String name, boolean enabled) throws CommandFormatException, IOException {
        ModelNode mn = ctx.buildRequest("/server-group=" + serverGroup
                + "/deployment=" + name + ":read-attribute(name=enabled)");
        ModelNode response = ctx.getModelControllerClient().execute(mn);
        if (response.hasDefined(Util.OUTCOME) && response.get(Util.OUTCOME).asString().equals(Util.SUCCESS)) {
            if (!response.hasDefined(RESULT)) {
                throw new CommandFormatException("No result for " + name);
            }
            if (!response.get(RESULT).asBoolean() == enabled) {
                throw new CommandFormatException(name + " not in right state");
            }
        } else {
            throw new CommandFormatException("Invalid response for " + name);
        }
    }
}
