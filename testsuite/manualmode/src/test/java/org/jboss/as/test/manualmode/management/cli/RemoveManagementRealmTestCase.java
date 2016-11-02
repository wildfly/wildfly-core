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
package org.jboss.as.test.manualmode.management.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.inject.Inject;
import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class RemoveManagementRealmTestCase {

    @Inject
    private ServerController container;

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();
    private Path target;
    private Path source;

    @Before
    public void beforeTest() throws Exception {
        container.start();
        String jbossDist = TestSuiteEnvironment.getSystemProperty("jboss.dist");
        source = Paths.get(jbossDist, "standalone", "configuration", "standalone.xml");
        target = Paths.get(temporaryUserHome.getRoot().getAbsolutePath(), "standalone.xml");
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @After
    public void afterTest() throws Exception {
        try {
            container.stop();
        } finally {
            Files.copy(target, source, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    public void testReload() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        cli.executeInteractive();
        cli.clearOutput();
        cli.pushLineAndWaitForResults("/core-service=management/security-realm=ManagementRealm/authentication=local:remove");
        cli.clearOutput();
        cli.pushLineAndWaitForResults("reload");
        assertTrue(cli.ctrlCAndWaitForClose());
    }

    @Test
    public void testShutdown() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        cli.executeInteractive();
        cli.clearOutput();
        cli.pushLineAndWaitForResults("/core-service=management/security-realm=ManagementRealm/authentication=local:remove");
        cli.clearOutput();
        cli.pushLineAndWaitForResults("shutdown --reload");
        assertTrue(cli.ctrlCAndWaitForClose());
    }

    @Test
    public void testAnyCommand() throws Exception {
        CliProcessWrapper cli1 = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        cli1.executeInteractive();
        cli1.clearOutput();

        CliProcessWrapper cli2 = new CliProcessWrapper().
                addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString()).
                addCliArgument("--controller=remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                addCliArgument("--connect");
        cli2.executeInteractive();
        cli2.clearOutput();

        cli1.pushLineAndWaitForResults("/core-service=management/security-realm=ManagementRealm/authentication=local:remove");
        cli1.clearOutput();
        cli1.pushLineAndWaitForResults("reload");
        assertTrue(cli1.ctrlCAndWaitForClose());

        // Send ls from cli2.
        cli2.pushLineAndWaitForResults("ls");
        cli2.clearOutput();
        assertTrue(cli2.ctrlCAndWaitForClose());
    }
}
