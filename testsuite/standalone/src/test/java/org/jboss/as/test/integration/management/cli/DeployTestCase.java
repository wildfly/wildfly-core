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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import static org.jboss.as.cli.Util.RESULT;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class DeployTestCase {

    private static File cliTestApp1War;
    private static File cliTestApp2War;
    private static File cliTestAnotherWar;

    private static CommandContext ctx;

    @BeforeClass
    public static void before() throws Exception {
        CommandContextConfiguration.Builder configBuilder = new CommandContextConfiguration.Builder();
        configBuilder.setInitConsole(true).setConsoleInput(System.in).setConsoleOutput(System.out).
                setController("remote+http://" + TestSuiteEnvironment.getServerAddress()
                        + ":" + TestSuiteEnvironment.getServerPort());
        ctx = CommandContextFactory.getInstance().newCommandContext(configBuilder.build());
        ctx.connectController();

        String tempDir = System.getProperty("java.io.tmpdir");

        // deployment1
        WebArchive war = ShrinkWrap.create(WebArchive.class, "cli-test-app1-deploy.war");
        war.addAsWebResource(new StringAsset("Version0"), "page.html");
        cliTestApp1War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp1War, true);

        // deployment2
        war = ShrinkWrap.create(WebArchive.class, "cli-test-app2-deploy.war");
        war.addAsWebResource(new StringAsset("Version1"), "page.html");
        cliTestApp2War = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestApp2War, true);

        // deployment3
        war = ShrinkWrap.create(WebArchive.class, "cli-test-another-deploy.war");
        war.addAsWebResource(new StringAsset("Version2"), "page.html");
        cliTestAnotherWar = new File(tempDir + File.separator + war.getName());
        new ZipExporterImpl(war).exportTo(cliTestAnotherWar, true);

        ctx.handle("deploy --disabled " + cliTestApp1War.getAbsolutePath());
        ctx.handle("deploy --disabled " + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deploy --disabled " + cliTestApp2War.getAbsolutePath());

    }

    @AfterClass
    public static void after() throws Exception {
        ctx.handle("undeploy *");
        ctx.terminateSession();
        cliTestApp1War.delete();
        cliTestApp2War.delete();
        cliTestAnotherWar.delete();
    }

    @Test
    public void testDeployAll() throws Exception {
        checkDeployment(cliTestApp1War.getName(), false);
        checkDeployment(cliTestAnotherWar.getName(), false);
        checkDeployment(cliTestApp2War.getName(), false);
        // Deploy them all.
        ctx.handle("deploy --name=<all>");
        checkDeployment(cliTestApp1War.getName(), true);
        checkDeployment(cliTestAnotherWar.getName(), true);
        checkDeployment(cliTestApp2War.getName(), true);
    }

    @Test
    public void testDeployAllCompletion() throws Exception {
        {
            String cmd = "deploy --name=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.contains("<all>"));
            assertTrue(candidates.toString(), candidates.contains(cliTestApp1War.getName()));
            assertTrue(candidates.toString(), candidates.contains(cliTestAnotherWar.getName()));
            assertTrue(candidates.toString(), candidates.contains(cliTestApp2War.getName()));
        }

        {
            String cmd = "deploy --name=<";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.contains("<all> "));
            assertTrue(candidates.toString(), candidates.size() == 1);
        }

        {
            String cmd = "deploy --name=<all>";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.contains("<all> "));
            assertTrue(candidates.toString(), candidates.size() == 1);
        }

    }

    private void checkDeployment(String name, boolean enabled) throws CommandFormatException, IOException {
        ModelNode mn = ctx.buildRequest("/deployment=" + name + ":read-attribute(name=enabled)");
        ModelNode response = ctx.getModelControllerClient().execute(mn);
        if (response.hasDefined(Util.OUTCOME) && response.get(Util.OUTCOME).asString().equals(Util.SUCCESS)) {
            if (!response.hasDefined(RESULT)) {
                throw new CommandFormatException("No result for " + name);
            }
            if (!response.get(RESULT).asBoolean() == enabled) {
                throw new CommandFormatException(name + " not in right state");
            }
        }
    }

}
